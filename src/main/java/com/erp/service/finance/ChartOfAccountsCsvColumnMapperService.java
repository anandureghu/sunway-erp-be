package com.erp.service.finance;

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
public class ChartOfAccountsCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "accountCode", "accountName", "type", "description",
            "parentAccountCode", "departmentName", "projectCode",
            "openingBalance", "accountNo"
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
                log.warn("OpenAI COA CSV mapping failed; falling back: {}", ex.getMessage());
            }
        }
        return new MappingResult(mapHeuristic(headers), false);
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
                You map CSV column headers from a chart of accounts spreadsheet onto ERP fields.
                Return ONLY a JSON object: {"mapping":{"<source header>":"<canonicalField or null>",...}}
                Rules:
                - Every source header must appear as a key.
                - Value must be one of the canonicalFields, or null.
                - accountCode = Account Code, GL Code, Code, Acc Code.
                - accountName = Account Name, Name, Description (only if no separate description column).
                - type = Type, Account Type (ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE/INCOME/CASH/TAX/COST/BUDGET).
                - description = Description, Remarks, Notes.
                - parentAccountCode = Parent Account, Parent Code, Parent.
                - departmentName = Department, Dept, Department Name.
                - projectCode = Project Code, Project.
                - openingBalance = Opening Balance, Balance, Initial Balance.
                - accountNo = Account No, Acc No, Account Number.
                - Never map two headers to the same canonical field.
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
            if (header == null || header.isBlank()) { result.put(header, null); continue; }
            String n = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String mapped = null;
            if ((n.contains("acccode") || n.contains("accountcode") || n.contains("glcode") || n.equals("code")) && !used.contains("accountCode"))
                mapped = "accountCode";
            else if ((n.contains("accname") || n.contains("accountname") || n.equals("name")) && !used.contains("accountName"))
                mapped = "accountName";
            else if ((n.equals("type") || n.contains("accounttype")) && !used.contains("type"))
                mapped = "type";
            else if ((n.contains("desc") || n.contains("remark") || n.contains("note")) && !used.contains("description"))
                mapped = "description";
            else if ((n.contains("parent")) && !used.contains("parentAccountCode"))
                mapped = "parentAccountCode";
            else if ((n.contains("dept") || n.contains("department")) && !used.contains("departmentName"))
                mapped = "departmentName";
            else if ((n.contains("project") || n.contains("projcode")) && !used.contains("projectCode"))
                mapped = "projectCode";
            else if ((n.contains("openingbalance") || n.contains("balance") || n.contains("initialbalance")) && !used.contains("openingBalance"))
                mapped = "openingBalance";
            else if ((n.contains("accno") || n.contains("accountno") || n.contains("accountnumber")) && !used.contains("accountNo"))
                mapped = "accountNo";
            if (mapped != null) used.add(mapped);
            result.put(header, mapped);
        }
        return result;
    }
}
