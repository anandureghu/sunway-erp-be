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
 * Maps arbitrary category/sub-category spreadsheet headers onto canonical fields.
 * Prefers OpenAI when configured; falls back to heuristics (Arabic columns ignored).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "categoryCode",
            "categoryName",
            "subCategoryCode",
            "subCategoryName",
            "glAccountCode",
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
                log.warn("OpenAI category CSV header mapping failed; falling back to heuristics: {}",
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
                You map CSV column headers from a category / sub-category master spreadsheet onto our ERP fields.
                Return ONLY a JSON object with this shape:
                {"mapping":{"<exact source header>":"<canonicalField or null>",...}}

                Rules:
                - Every source header from the input MUST appear as a key in mapping.
                - Use the exact source header text as the key (do not rename keys).
                - Value must be one of the canonicalFields, or null to ignore the column.
                - NEVER map Arabic / AR / Name (AR) columns — always set those to null.
                - categoryCode = top-level category code (e.g. CAT-001).
                - categoryName = English category name (Category Name (EN), Category Name).
                - subCategoryCode = sub-category code (e.g. SC-001).
                - subCategoryName = sub-category name.
                - glAccountCode = optional GL / COA account code (GL Account Code, Account Code).
                - status = Active / Inactive.
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
            if (canonical == null) {
                cleaned.put(header, null);
                continue;
            }
            if (usedCanonical.contains(canonical)) {
                cleaned.put(header, null);
                continue;
            }
            usedCanonical.add(canonical);
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
        // Explicit AR markers: (AR), [AR], " Name AR", "_ar", "-ar"
        if (lower.contains("(ar)") || lower.contains("[ar]") || lower.contains("（ar）")) {
            return true;
        }
        if (lower.endsWith(" ar") || lower.endsWith("_ar") || lower.endsWith("-ar")) {
            return true;
        }
        String n = normalize(header);
        // namear / categorynamear — not "categoryname" (which contains "ar" inside "category")
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

        if (n.equals("glaccountcode") || n.equals("glcode") || n.equals("glaccount")
                || n.equals("accountcode") || n.equals("coa") || n.equals("coacode")
                || n.equals("ledgercode") || n.equals("gl")) {
            return "glAccountCode";
        }
        if (n.equals("status") || n.equals("categorystatus") || n.equals("activestatus")) {
            return "status";
        }
        if (n.equals("subcategorycode") || n.equals("subcatcode") || n.equals("sccode")
                || n.equals("subcode") || n.equals("childcode")) {
            return "subCategoryCode";
        }
        if (n.equals("subcategoryname") || n.equals("subcatname") || n.equals("subcategory")
                || n.equals("subcat") || n.equals("childname") || n.equals("scname")) {
            return "subCategoryName";
        }
        if (n.equals("categorycode") || n.equals("catcode") || n.equals("parentcode")
                || n.equals("maincategorycode")) {
            return "categoryCode";
        }
        if (n.equals("categoryname") || n.equals("categorynameen") || n.equals("categoryen")
                || n.equals("category") || n.equals("parentname") || n.equals("maincategory")
                || n.equals("nameen") || n.equals("englishname")) {
            return "categoryName";
        }
        // Loose contains matches (order matters: sub before parent)
        if (n.contains("sub") && n.contains("code")) {
            return "subCategoryCode";
        }
        if (n.contains("sub") && (n.contains("name") || n.equals("subcategory"))) {
            return "subCategoryName";
        }
        if (n.contains("gl") || (n.contains("account") && n.contains("code"))) {
            return "glAccountCode";
        }
        if (n.contains("category") && n.contains("code") && !n.contains("sub")) {
            return "categoryCode";
        }
        if (n.contains("category") && n.contains("name") && !n.contains("sub") && !n.contains("ar")) {
            return "categoryName";
        }
        return null;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
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
