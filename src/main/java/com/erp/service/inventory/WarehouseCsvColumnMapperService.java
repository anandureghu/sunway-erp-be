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
 * Maps arbitrary warehouse spreadsheet headers onto canonical fields.
 * Prefers OpenAI when configured; falls back to heuristics (Arabic columns ignored).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "warehouseCode",
            "warehouseName",
            "warehouseType",
            "street",
            "city",
            "manager",
            "phone",
            "capacity",
            "status",
            "country",
            "pin",
            "contactPersonName"
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
                log.warn("OpenAI warehouse CSV header mapping failed; falling back to heuristics: {}",
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
                    String header = headers.get(i);
                    String value = i < row.size() ? row.get(i) : "";
                    sample.put(header, value == null ? "" : value);
                }
                samples.add(sample);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("sampleRows", samples);
        payload.put("canonicalFields", CANONICAL_FIELDS.stream().sorted().toList());

        String system = """
                You map CSV column headers from a warehouse master spreadsheet onto our ERP fields.
                Return ONLY a JSON object with this shape:
                {"mapping":{"<exact source header>":"<canonicalField or null>",...}}

                Rules:
                - Every source header from the input MUST appear as a key in mapping.
                - Use the exact source header text as the key (do not rename keys).
                - Value must be one of the canonicalFields, or null to ignore the column.
                - NEVER map Arabic / AR / Name (AR) columns — always set those to null.
                - warehouseCode = Warehouse Code / WH Code (e.g. WH-001).
                - warehouseName = English warehouse name (Warehouse Name (EN), Warehouse Name).
                - warehouseType = Type (General, Hazardous, Site Store, MAIN, …).
                - street = Address / Street.
                - city = City.
                - manager = Manager name (free text; we resolve to a user when possible).
                - phone = Contact No. / Phone / Mobile.
                - capacity = Capacity (Pallets) / Capacity.
                - status = Active / Inactive.
                - country, pin, contactPersonName when present.
                - Do not invent headers. Do not map two headers to the same canonical field;
                  if conflict, keep the best match and set the other to null.
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
                mappingNode,
                new TypeReference<Map<String, String>>() {}
        );

        Map<String, String> cleaned = new LinkedHashMap<>();
        Set<String> usedCanonical = new java.util.HashSet<>();
        for (String header : headers) {
            if (isArabicHeader(header)) {
                cleaned.put(header, null);
                continue;
            }
            String target = raw.get(header);
            if (target == null || target.isBlank() || "null".equalsIgnoreCase(target)
                    || "ignore".equalsIgnoreCase(target) || "skip".equalsIgnoreCase(target)) {
                cleaned.put(header, null);
                continue;
            }
            String canonical = resolveCanonical(target);
            if (canonical == null || !usedCanonical.add(canonical)) {
                cleaned.put(header, null);
                continue;
            }
            cleaned.put(header, canonical);
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
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (String field : CANONICAL_FIELDS) {
            if (field.equalsIgnoreCase(trimmed)) {
                return field;
            }
        }
        String norm = normalize(trimmed);
        for (String field : CANONICAL_FIELDS) {
            if (normalize(field).equals(norm)) {
                return field;
            }
        }
        return guessCanonical(trimmed);
    }

    static boolean isArabicHeader(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String lower = header.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("arabic")) {
            return true;
        }
        if (lower.contains("(ar)") || lower.contains("[ar]") || lower.contains("（ar）")) {
            return true;
        }
        if (lower.endsWith(" ar") || lower.endsWith("_ar") || lower.endsWith("-ar")) {
            return true;
        }
        String n = normalize(header);
        return n.endsWith("namear") || n.equals("ar") || n.endsWith("arabic");
    }

    private static String guessCanonical(String header) {
        if (isArabicHeader(header)) {
            return null;
        }
        String n = normalize(header);
        if (n.isEmpty()) {
            return null;
        }

        if (n.equals("warehousecode") || n.equals("whcode") || n.equals("code")
                || n.equals("warehouseno") || n.equals("locationcode")) {
            return "warehouseCode";
        }
        if (n.equals("warehousename") || n.equals("warehousenameen") || n.equals("name")
                || n.equals("nameen") || n.equals("warehouse") || n.equals("storename")) {
            return "warehouseName";
        }
        if (n.equals("type") || n.equals("warehousetype") || n.equals("whtype")
                || n.equals("category") || n.equals("storetype")) {
            return "warehouseType";
        }
        if (n.equals("address") || n.equals("street") || n.equals("streetaddress")
                || n.equals("location") || n.equals("addr")) {
            return "street";
        }
        if (n.equals("city") || n.equals("town")) {
            return "city";
        }
        if (n.equals("manager") || n.equals("warehousemanager") || n.equals("managername")
                || n.equals("incharge") || n.equals("supervisor")) {
            return "manager";
        }
        if (n.equals("contactno") || n.equals("contactnumber") || n.equals("phone")
                || n.equals("mobile") || n.equals("phoneno") || n.equals("telephone")
                || n.equals("contact")) {
            return "phone";
        }
        if (n.equals("capacity") || n.equals("capacitypallets") || n.equals("pallets")
                || n.equals("palletcapacity") || n.equals("maxcapacity")) {
            return "capacity";
        }
        if (n.equals("status") || n.equals("warehousestatus") || n.equals("activestatus")) {
            return "status";
        }
        if (n.equals("country")) {
            return "country";
        }
        if (n.equals("pin") || n.equals("pincode") || n.equals("zip") || n.equals("zipcode")
                || n.equals("postalcode")) {
            return "pin";
        }
        if (n.equals("contactperson") || n.equals("contactpersonname") || n.equals("contactname")) {
            return "contactPersonName";
        }

        if (n.contains("capacity") || n.contains("pallet")) {
            return "capacity";
        }
        if (n.contains("phone") || (n.contains("contact") && n.contains("no"))) {
            return "phone";
        }
        if (n.contains("manager")) {
            return "manager";
        }
        if (n.contains("type")) {
            return "warehouseType";
        }
        if (n.contains("city")) {
            return "city";
        }
        if ((n.contains("address") || n.contains("street")) && !n.contains("email")) {
            return "street";
        }
        if (n.contains("code") && (n.contains("warehouse") || n.contains("wh") || n.equals("code"))) {
            return "warehouseCode";
        }
        if (n.contains("name") && !n.contains("manager") && !n.contains("contact")) {
            return "warehouseName";
        }
        return null;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            if (firstNl > 0) {
                trimmed = trimmed.substring(firstNl + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
