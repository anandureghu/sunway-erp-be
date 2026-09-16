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
public class TransactionCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "transactionDate", "transactionType", "amount",
            "debitAccountCode", "creditAccountCode",
            "transactionDescription", "source"
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
                log.warn("OpenAI transaction CSV mapping failed; falling back: {}", ex.getMessage());
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
                You map CSV column headers from a financial transactions spreadsheet onto ERP fields.
                Return ONLY a JSON object: {"mapping":{"<source header>":"<canonicalField or null>",...}}
                Rules:
                - Every source header must appear as a key.
                - Value must be one of the canonicalFields, or null.
                - transactionDate = Date, Transaction Date, Txn Date.
                - transactionType = Type, Transaction Type (PAYMENT/JOURNAL/RECEIPT/TRANSFER).
                - amount = Amount, Value, Txn Amount.
                - debitAccountCode = Debit Account, Dr Account, Debit Code, Dr.
                - creditAccountCode = Credit Account, Cr Account, Credit Code, Cr.
                - transactionDescription = Description, Narration, Details, Remarks, Notes.
                - source = Source, Reference, Ref.
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
            if ((n.contains("date") || n.equals("txndate")) && !used.contains("transactionDate"))
                mapped = "transactionDate";
            else if ((n.equals("type") || n.contains("txntype") || n.contains("transactiontype")) && !used.contains("transactionType"))
                mapped = "transactionType";
            else if ((n.equals("amount") || n.contains("value") || n.contains("txnamount")) && !used.contains("amount"))
                mapped = "amount";
            else if ((n.contains("debit") || n.equals("dr")) && !used.contains("debitAccountCode"))
                mapped = "debitAccountCode";
            else if ((n.contains("credit") || n.equals("cr")) && !used.contains("creditAccountCode"))
                mapped = "creditAccountCode";
            else if ((n.contains("desc") || n.contains("narration") || n.contains("detail") || n.contains("remark") || n.contains("note")) && !used.contains("transactionDescription"))
                mapped = "transactionDescription";
            else if ((n.equals("source") || n.contains("reference") || n.equals("ref")) && !used.contains("source"))
                mapped = "source";
            if (mapped != null) used.add(mapped);
            result.put(header, mapped);
        }
        return result;
    }
}
