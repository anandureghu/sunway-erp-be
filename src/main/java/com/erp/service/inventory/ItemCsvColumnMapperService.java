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
 * Maps arbitrary CSV column titles onto canonical inventory item fields.
 * Prefers OpenAI when configured; falls back to heuristic name matching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemCsvColumnMapperService {

    public static final Set<String> CANONICAL_FIELDS = Set.of(
            "sku",
            "name",
            "category",
            "warehouse",
            "quantity",
            "unitMeasure",
            "barcode",
            "brand",
            "type",
            "subCategory",
            "costPrice",
            "sellingPrice",
            "status",
            "reorderLevel",
            "minimum",
            "maximum",
            "location",
            "description",
            "serialNo",
            "dateReceived",
            "expiryDate"
    );

    private final OpenAiChatClient openAiChatClient;
    private final AssistantOpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public record MappingResult(Map<String, String> mapping, boolean aiMapped) {}

    /**
     * @param headers original CSV header labels (order preserved)
     * @param sampleRows up to a few data rows to help disambiguate columns
     * @return map of original header → canonical field name, or null when the column
     *         should be kept in item metadata (or ignored if empty)
     */
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
                log.warn("OpenAI CSV header mapping failed; falling back to heuristics: {}", ex.getMessage());
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
                You map CSV column headers from a supplier/customer inventory spreadsheet onto our ERP item fields.
                Return ONLY a JSON object with this shape:
                {"mapping":{"<exact source header>":"<canonicalField or null>",...}}

                Rules:
                - Every source header from the input MUST appear as a key in mapping.
                - Use the exact source header text as the key (do not rename keys).
                - Value must be one of the canonicalFields, or null when the column should NOT become a first-class field
                  (it will be stored in item metadata instead).
                - Prefer: sku, name, category, warehouse for identity. Map product code/item code/part number → sku.
                - Map item name/description/title → name (prefer a short name field over long description when both exist;
                  long notes → description).
                - Map UOM/unit → unitMeasure, cost/purchase price → costPrice, sell/retail price → sellingPrice.
                - Map stock/qty/on hand → quantity, reorder/min stock → reorderLevel.
                - Do not invent headers. Do not map two different headers to the same canonical field unless one is clearly
                  a duplicate; if conflict, keep the best match and set the other to null.
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
            // Some models return the map at the root
            mappingNode = root;
        }

        Map<String, String> raw = objectMapper.convertValue(
                mappingNode,
                new TypeReference<Map<String, String>>() {}
        );

        Map<String, String> cleaned = new LinkedHashMap<>();
        Set<String> usedCanonical = new java.util.HashSet<>();
        for (String header : headers) {
            String target = raw.get(header);
            if (target == null || target.isBlank() || "null".equalsIgnoreCase(target)
                    || "metadata".equalsIgnoreCase(target) || "ignore".equalsIgnoreCase(target)) {
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

    private static String guessCanonical(String header) {
        String n = normalize(header);
        if (n.isEmpty()) {
            return null;
        }
        if (n.equals("sku") || n.equals("itemcode") || n.equals("productcode") || n.equals("partno")
                || n.equals("partnumber") || n.equals("itemno") || n.equals("itemnumber")
                || n.equals("code") || n.equals("articlenumber") || n.equals("articleno")) {
            return "sku";
        }
        if (n.equals("name") || n.equals("itemname") || n.equals("productname") || n.equals("title")
                || n.equals("item") || n.equals("product")) {
            return "name";
        }
        if (n.equals("category") || n.equals("itemcategory") || n.equals("productcategory")
                || n.equals("group") || n.equals("dept") || n.equals("department")) {
            return "category";
        }
        if (n.equals("warehouse") || n.equals("warehousename") || n.equals("warehouseid")
                || n.equals("locationwarehouse") || n.equals("store") || n.equals("storename")) {
            return "warehouse";
        }
        if (n.equals("quantity") || n.equals("qty") || n.equals("stock") || n.equals("onhand")
                || n.equals("stockqty") || n.equals("qtyonhand")) {
            return "quantity";
        }
        if (n.equals("unitmeasure") || n.equals("uom") || n.equals("unit") || n.equals("units")
                || n.equals("measure")) {
            return "unitMeasure";
        }
        if (n.equals("barcode") || n.equals("ean") || n.equals("upc") || n.equals("gtin")) {
            return "barcode";
        }
        if (n.equals("brand") || n.equals("manufacturer") || n.equals("make")) {
            return "brand";
        }
        if (n.equals("type") || n.equals("itemtype") || n.equals("producttype")) {
            return "type";
        }
        if (n.equals("subcategory") || n.equals("subcat") || n.equals("subclass")) {
            return "subCategory";
        }
        if (n.equals("costprice") || n.equals("cost") || n.equals("purchaseprice")
                || n.equals("unitcost") || n.equals("buyprice")) {
            return "costPrice";
        }
        if (n.equals("sellingprice") || n.equals("sellprice") || n.equals("saleprice")
                || n.equals("retailprice") || n.equals("price") || n.equals("unitprice")) {
            return "sellingPrice";
        }
        if (n.equals("status") || n.equals("itemstatus")) {
            return "status";
        }
        if (n.equals("reorderlevel") || n.equals("reorder") || n.equals("reorderpoint")
                || n.equals("minstock") || n.equals("safetystock")) {
            return "reorderLevel";
        }
        if (n.equals("minimum") || n.equals("min") || n.equals("minqty")) {
            return "minimum";
        }
        if (n.equals("maximum") || n.equals("max") || n.equals("maxqty")) {
            return "maximum";
        }
        if (n.equals("location") || n.equals("bin") || n.equals("shelflocation") || n.equals("aisle")) {
            return "location";
        }
        if (n.equals("description") || n.equals("desc") || n.equals("details") || n.equals("notes")
                || n.equals("remarks")) {
            return "description";
        }
        if (n.equals("serialno") || n.equals("serial") || n.equals("serialnumber")) {
            return "serialNo";
        }
        if (n.equals("datereceived") || n.equals("receiveddate") || n.equals("receiptdate")) {
            return "dateReceived";
        }
        if (n.equals("expirydate") || n.equals("expirationdate") || n.equals("expdate")
                || n.equals("salebydate") || n.equals("bestbefore")) {
            return "expiryDate";
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
