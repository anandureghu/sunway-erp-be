package com.erp.service.hr;

import com.erp.assistant.AssistantOpenAiProperties;
import com.erp.assistant.OpenAiChatClient;
import com.erp.assistant.OpenAiChatClient.OpenAiChatResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "departmentCode", "departmentName", "description"
    );

    private final OpenAiChatClient openAiChatClient;
    private final AssistantOpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public record MappingResult(Map<String, String> mapping, boolean aiMapped) {}

    public MappingResult mapHeaders(List<String> headers, List<List<String>> sampleRows) {
        if (headers == null || headers.isEmpty()) return new MappingResult(Map.of(), false);
        if (openAiProperties.isConfigured()) {
            try {
                Map<String, String> ai = mapWithOpenAi(headers, sampleRows);
                if (ai != null && !ai.isEmpty()) return new MappingResult(ai, true);
            } catch (Exception ex) {
                log.warn("OpenAI dept CSV mapping failed; falling back: {}", ex.getMessage());
            }
        }
        return new MappingResult(mapHeuristic(headers), false);
    }

    public static boolean isArabicHeader(String h) {
        if (h == null) return false;
        String n = h.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return n.endsWith("ar") || n.contains("arabic") || n.contains("عربي");
    }

    private Map<String, String> mapWithOpenAi(List<String> headers, List<List<String>> sampleRows) throws Exception {
        List<Map<String, String>> samples = new ArrayList<>();
        if (sampleRows != null) {
            for (List<String> row : sampleRows) {
                Map<String, String> sample = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++)
                    sample.put(headers.get(i), i < row.size() && row.get(i) != null ? row.get(i) : "");
                samples.add(sample);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("sampleRows", samples);
        payload.put("canonicalFields", CANONICAL_FIELDS.stream().sorted().toList());

        String system = """
                You map CSV column headers from a department master spreadsheet onto our ERP fields.
                Return ONLY a JSON object: {"mapping":{"<source header>":"<canonicalField or null>",...}}
                Rules:
                - Every source header must appear as a key.
                - Value must be one of: departmentCode, departmentName, description, or null.
                - departmentCode = Dept Code, Code, Department Code, Dept No, ID.
                - departmentName = Dept Name, Name, Department, Department Name (English only).
                - description = Description, Remarks, Notes.
                - Never map Arabic/AR columns — always null.
                - Do not map two headers to the same canonical field.
                """;
        String user = "Map these CSV headers:\n" + objectMapper.writeValueAsString(payload);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        );
        OpenAiChatResult result = openAiChatClient.complete(messages, null, true);
        String content = result.content();
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < 0) return null;
        JsonNode root = objectMapper.readTree(content.substring(start, end + 1));
        JsonNode mappingNode = root.get("mapping");
        if (mappingNode == null || !mappingNode.isObject()) return null;
        Map<String, String> raw = objectMapper.convertValue(mappingNode, new TypeReference<>() {});
        Map<String, String> cleaned = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String h : headers) {
            String val = raw.getOrDefault(h, null);
            if (val != null && (val.isBlank() || !CANONICAL_FIELDS.contains(val) || used.contains(val))) val = null;
            if (val != null) used.add(val);
            cleaned.put(h, val);
        }
        return cleaned;
    }

    private Map<String, String> mapHeuristic(List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank() || isArabicHeader(header)) {
                result.put(header, null);
                continue;
            }
            String n = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String mapped = null;
            if ((n.contains("code") || n.equals("id") || n.equals("deptno")) && !used.contains("departmentCode"))
                mapped = "departmentCode";
            else if ((n.contains("name") || n.equals("department") || n.equals("dept")) && !used.contains("departmentName"))
                mapped = "departmentName";
            else if ((n.contains("desc") || n.contains("remark") || n.contains("note")) && !used.contains("description"))
                mapped = "description";
            if (mapped != null) used.add(mapped);
            result.put(header, mapped);
        }
        return result;
    }
}
