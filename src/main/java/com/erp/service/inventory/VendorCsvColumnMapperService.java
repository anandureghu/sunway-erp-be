package com.erp.service.inventory;

import com.erp.assistant.AssistantOpenAiProperties;
import com.erp.assistant.OpenAiChatClient;
import com.erp.assistant.OpenAiChatClient.OpenAiChatResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps arbitrary supplier spreadsheet headers onto canonical vendor fields.
 * Prefers OpenAI when configured; falls back to heuristics (Arabic columns ignored).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendorCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "vendorCode",
            "vendorName",
            "categoryName",
            "vendorCrNo",
            "taxId",
            "contactPersonName",
            "phoneNo",
            "email",
            "street",
            "city",
            "country",
            "paymentTerms",
            "currencyCode",
            "bankName",
            "iban",
            "creditLimit",
            "status"
    );

    private final OpenAiChatClient openAiChatClient;
    private final AssistantOpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public record MappingResult(Map<String, String> mapping, boolean aiMapped) {}

    public MappingResult mapHeaders(List<String> headers, List<List<String>> sampleRows) {
        if (headers == null || headers.isEmpty()) {
            return new MappingResult(Map.of(), false);
        }
        if (openAiProperties.isConfigured()) {
            try {
                Map<String, String> ai = mapWithOpenAi(headers, sampleRows);
                if (ai != null && !ai.isEmpty()) {
                    return new MappingResult(ai, true);
                }
            } catch (Exception ex) {
                log.warn("OpenAI vendor CSV header mapping failed; falling back to heuristics: {}",
                        ex.getMessage());
            }
        }
        return new MappingResult(mapHeuristic(headers), false);
    }

    private Map<String, String> mapWithOpenAi(List<String> headers, List<List<String>> sampleRows)
            throws Exception {
        List<Map<String, String>> samples = new ArrayList<>();
        if (sampleRows != null) {
            for (List<String> row : sampleRows) {
                Map<String, String> sample = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    sample.put(headers.get(i), i < row.size() && row.get(i) != null ? row.get(i) : "");
                }
                samples.add(sample);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("sampleRows", samples);
        payload.put("canonicalFields", CANONICAL_FIELDS.stream().sorted().toList());

        String system = """
                You map CSV column headers from a supplier / vendor master spreadsheet onto our ERP fields.
                Return ONLY a JSON object with this shape:
                {"mapping":{"<exact source header>":"<canonicalField or null>",...}}

                Rules:
                - Every source header from the input MUST appear as a key in mapping.
                - Use the exact source header text as the key.
                - Value must be one of the canonicalFields, or null to ignore.
                - NEVER map Arabic / AR / Name (AR) columns — always null.
                - vendorCode = Supplier Code / Vendor Code (SUP-001).
                - vendorName = Supplier Name (EN) / Vendor Name (English only).
                - categoryName = Category / Supplier Category (HVAC, Electrical, …).
                - vendorCrNo = CR Number / Commercial Registration.
                - taxId = VAT / TIN / Tax ID.
                - contactPersonName = Contact Person.
                - phoneNo = Phone / Mobile / Contact No.
                - email = Email.
                - street = Address / Street.
                - city, country, paymentTerms, currencyCode, bankName, iban.
                - creditLimit = Credit Limit (QAR) / Credit Limit.
                - status = Active / Inactive.
                - Do not map two headers to the same canonical field.
                """;

        String user = "Map these CSV headers:\n" + objectMapper.writeValueAsString(payload);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );

        OpenAiChatResult result = openAiChatClient.complete(messages, null, true);
        String content = result.content();
        if (content == null || content.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(stripCodeFence(content));
        JsonNode mappingNode = root.path("mapping");
        if (!mappingNode.isObject()) {
            mappingNode = root;
        }

        Map<String, String> raw = objectMapper.convertValue(
                mappingNode, new TypeReference<Map<String, String>>() {});

        Map<String, String> cleaned = new LinkedHashMap<>();
        Set<String> used = new java.util.HashSet<>();
        for (String header : headers) {
            if (isArabicHeader(header)) {
                cleaned.put(header, null);
                continue;
            }
            String target = raw.get(header);
            if (target == null || target.isBlank() || "null".equalsIgnoreCase(target)
                    || "ignore".equalsIgnoreCase(target)) {
                cleaned.put(header, null);
                continue;
            }
            String canonical = resolveCanonical(target);
            if (canonical == null || !used.add(canonical)) {
                cleaned.put(header, null);
            } else {
                cleaned.put(header, canonical);
            }
        }
        return cleaned;
    }

    private Map<String, String> mapHeuristic(List<String> headers) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new java.util.HashSet<>();
        for (String header : headers) {
            if (isArabicHeader(header)) {
                mapping.put(header, null);
                continue;
            }
            String canonical = guessCanonical(header);
            if (canonical != null && used.add(canonical)) {
                mapping.put(header, canonical);
            } else {
                mapping.put(header, null);
            }
        }
        return mapping;
    }

    private static String resolveCanonical(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        for (String field : CANONICAL_FIELDS) {
            if (field.equalsIgnoreCase(trimmed) || normalize(field).equals(normalize(trimmed))) {
                return field;
            }
        }
        return guessCanonical(trimmed);
    }

    static boolean isArabicHeader(String header) {
        if (header == null || header.isBlank()) return false;
        String lower = header.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("arabic") || lower.contains("(ar)") || lower.contains("[ar]")) return true;
        if (lower.endsWith(" ar") || lower.endsWith("_ar") || lower.endsWith("-ar")) return true;
        String n = normalize(header);
        return n.endsWith("namear") || n.equals("ar") || n.endsWith("arabic");
    }

    private static String guessCanonical(String header) {
        if (isArabicHeader(header)) return null;
        String n = normalize(header);
        if (n.isEmpty()) return null;

        if (n.equals("suppliercode") || n.equals("vendorcode") || n.equals("supcode")
                || n.equals("code") || n.equals("vendorno")) return "vendorCode";
        if (n.equals("suppliername") || n.equals("suppliernameen") || n.equals("vendorname")
                || n.equals("vendornameen") || n.equals("name") || n.equals("nameen")
                || n.equals("supplier") || n.equals("vendor")) return "vendorName";
        if (n.equals("category") || n.equals("suppliercategory") || n.equals("vendorcategory")
                || n.equals("categoryname")) return "categoryName";
        if (n.equals("crnumber") || n.equals("crno") || n.equals("vendorcrno")
                || n.equals("commercialregistration") || n.equals("cr")) return "vendorCrNo";
        if (n.equals("vat") || n.equals("tin") || n.equals("vattin") || n.equals("taxid")
                || n.equals("tax") || n.equals("vatnumber") || n.equals("taxnumber")) return "taxId";
        if (n.equals("contactperson") || n.equals("contactpersonname") || n.equals("contactname")
                || n.equals("contact")) return "contactPersonName";
        if (n.equals("phone") || n.equals("phoneno") || n.equals("mobile") || n.equals("telephone")
                || n.equals("contactno") || n.equals("contactnumber")) return "phoneNo";
        if (n.equals("email") || n.equals("emailaddress") || n.equals("mail")) return "email";
        if (n.equals("address") || n.equals("street") || n.equals("streetaddress") || n.equals("addr"))
            return "street";
        if (n.equals("city") || n.equals("town")) return "city";
        if (n.equals("country")) return "country";
        if (n.equals("paymentterms") || n.equals("terms") || n.equals("paymentterm")
                || n.equals("payterms")) return "paymentTerms";
        if (n.equals("currency") || n.equals("currencycode") || n.equals("curr")) return "currencyCode";
        if (n.equals("bankname") || n.equals("bank") || n.equals("banker")) return "bankName";
        if (n.equals("iban") || n.equals("accountiban") || n.equals("bankaccount")) return "iban";
        if (n.equals("creditlimit") || n.equals("creditlimitqar") || n.equals("credit")
                || n.equals("limit")) return "creditLimit";
        if (n.equals("status") || n.equals("supplierstatus") || n.equals("vendorstatus")
                || n.equals("activestatus")) return "status";

        if (n.contains("credit") && n.contains("limit")) return "creditLimit";
        if (n.contains("iban")) return "iban";
        if (n.contains("bank")) return "bankName";
        if (n.contains("payment") && n.contains("term")) return "paymentTerms";
        if (n.contains("currency")) return "currencyCode";
        if (n.contains("category")) return "categoryName";
        if (n.contains("vat") || n.contains("tin") || (n.contains("tax") && !n.contains("contact")))
            return "taxId";
        if (n.contains("cr") && (n.contains("no") || n.contains("number") || n.equals("cr")))
            return "vendorCrNo";
        if (n.contains("email") || n.contains("mail")) return "email";
        if (n.contains("phone") || n.contains("mobile") || (n.contains("contact") && n.contains("no")))
            return "phoneNo";
        if (n.contains("contact") && n.contains("person")) return "contactPersonName";
        if (n.contains("city")) return "city";
        if (n.contains("country")) return "country";
        if (n.contains("address") || n.contains("street")) return "street";
        if (n.contains("code") && (n.contains("supplier") || n.contains("vendor") || n.contains("sup")))
            return "vendorCode";
        if (n.contains("name") && !n.contains("bank") && !n.contains("contact")) return "vendorName";
        if (n.contains("status")) return "status";
        return null;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            if (firstNl > 0) trimmed = trimmed.substring(firstNl + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
