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
            "subCategory",
            "warehouse",
            "preferredSupplier",
            "quantity",
            "unitMeasure",
            "barcode",
            "brand",
            "type",
            "model",
            "manufacturerPartNumber",
            "costPrice",
            "sellingPrice",
            "status",
            "reorderLevel",
            "reorderQty",
            "minimum",
            "maximum",
            "location",
            "description",
            "serialNo",
            "dateReceived",
            "expiryDate",
            "criticality",
            "hsnCode",
            "vatApplicable",
            "leadTimeDays",
            "supplierPartNo",
            "weightKg",
            "dimensions",
            "warrantyMonths",
            "remarks"
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
                You map CSV column headers from an inventory item master spreadsheet onto our ERP item fields.
                Return ONLY a JSON object with this shape:
                {"mapping":{"<exact source header>":"<canonicalField or null>",...}}

                Rules:
                - Every source header from the input MUST appear as a key in mapping.
                - Use the exact source header text as the key (do not rename keys).
                - Value must be one of the canonicalFields, or null when the column should be ignored
                  (Arabic names) or stored in metadata.
                - NEVER map Arabic / AR / Name (AR) columns — always set those to null.
                - Item Code / SKU → sku. Item Name (EN) → name.
                - Category → category. Sub-Category → subCategory.
                - UOM → unitMeasure. Brand / Manufacturer → brand.
                - Model / Part No. → model OR manufacturerPartNumber (prefer manufacturerPartNumber for part numbers).
                - Item Type → type. Criticality → criticality. HSN / Tariff → hsnCode.
                - Unit Cost → costPrice. Selling Price → sellingPrice.
                - VAT Applicable → vatApplicable. Reorder Level → reorderLevel. Reorder Qty → reorderQty.
                - Min Stock → minimum. Max Stock → maximum. Lead Time → leadTimeDays.
                - Default Warehouse → warehouse. Shelf / Bin → location.
                - Preferred Supplier → preferredSupplier. Supplier Part No. → supplierPartNo.
                - Weight → weightKg. Dimensions → dimensions. Warranty → warrantyMonths.
                - On Hand Quantity / Qty → quantity. Date Received → dateReceived.
                - Sale by Date / Expiry / Best Before → expiryDate (accept DD/MM/YYYY).
                - Remarks / notes → remarks (not description when both exist).
                - Do not invent headers. Do not map two headers to the same canonical field.
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
            if (isArabicHeader(header)) {
                cleaned.put(header, null);
                continue;
            }
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

    private static String resolveCanonical(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (String field : CANONICAL_FIELDS) {
            if (field.equalsIgnoreCase(trimmed)) {
                return field;
            }
        }
        return guessCanonical(trimmed);
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

    static boolean isArabicHeader(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String lower = header.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("arabic") || lower.contains("(ar)") || lower.contains("[ar]")) {
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
        if (n.equals("sku") || n.equals("itemcode") || n.equals("productcode") || n.equals("partno")
                || n.equals("partnumber") || n.equals("itemno") || n.equals("itemnumber")
                || n.equals("code") || n.equals("articlenumber") || n.equals("articleno")) {
            return "sku";
        }
        if (n.equals("name") || n.equals("itemname") || n.equals("itemnameen") || n.equals("productname")
                || n.equals("title") || n.equals("item") || n.equals("product") || n.equals("nameen")) {
            return "name";
        }
        if (n.equals("subcategory") || n.equals("subcat") || n.equals("subclass")
                || n.equals("subcategoryname")) {
            return "subCategory";
        }
        if (n.equals("category") || n.equals("itemcategory") || n.equals("productcategory")
                || n.equals("group") || n.equals("dept") || n.equals("department")) {
            return "category";
        }
        if (n.equals("warehouse") || n.equals("warehousename") || n.equals("warehouseid")
                || n.equals("defaultwarehouse") || n.equals("locationwarehouse") || n.equals("store")
                || n.equals("storename") || n.equals("whcode") || n.equals("warehousecode")) {
            return "warehouse";
        }
        if (n.equals("preferredsupplier") || n.equals("supplier") || n.equals("vendor")
                || n.equals("preferredvendor") || n.equals("suppliername") || n.equals("vendorname")) {
            return "preferredSupplier";
        }
        if (n.equals("supplierpartno") || n.equals("supplierpartnumber") || n.equals("vendorpartno")
                || n.equals("vendorpartnumber")) {
            return "supplierPartNo";
        }
        if (n.equals("quantity") || n.equals("qty") || n.equals("stock") || n.equals("onhand")
                || n.equals("stockqty") || n.equals("qtyonhand") || n.equals("onhandquantity")
                || n.equals("onhandqty") || n.equals("quantityonhand")) {
            return "quantity";
        }
        if (n.equals("unitmeasure") || n.equals("uom") || n.equals("unit") || n.equals("units")
                || n.equals("measure")) {
            return "unitMeasure";
        }
        if (n.equals("barcode") || n.equals("ean") || n.equals("upc") || n.equals("gtin")) {
            return "barcode";
        }
        if (n.equals("brand") || n.equals("manufacturer") || n.equals("make")
                || n.equals("brandmanufacturer")) {
            return "brand";
        }
        if (n.equals("type") || n.equals("itemtype") || n.equals("producttype")) {
            return "type";
        }
        if (n.equals("model") || n.equals("modelno") || n.equals("modelnumber")
                || n.equals("modelpartno")) {
            return "model";
        }
        if (n.equals("manufacturerpartnumber") || n.equals("mpn") || n.equals("manufacturerpartno")) {
            return "manufacturerPartNumber";
        }
        if (n.equals("costprice") || n.equals("cost") || n.equals("purchaseprice")
                || n.equals("unitcost") || n.equals("buyprice") || n.equals("unitcostqar")) {
            return "costPrice";
        }
        if (n.equals("sellingprice") || n.equals("sellprice") || n.equals("saleprice")
                || n.equals("retailprice") || n.equals("price") || n.equals("unitprice")
                || n.equals("sellingpriceqar") || n.equals("priceqar")) {
            return "sellingPrice";
        }
        if (n.equals("status") || n.equals("itemstatus")) {
            return "status";
        }
        if (n.equals("reorderqty") || n.equals("reorderquantity") || n.equals("reorderq")) {
            return "reorderQty";
        }
        if (n.equals("reorderlevel") || n.equals("reorder") || n.equals("reorderpoint")
                || n.equals("safetystock")) {
            return "reorderLevel";
        }
        if (n.equals("minimum") || n.equals("min") || n.equals("minqty") || n.equals("minstock")) {
            return "minimum";
        }
        if (n.equals("maximum") || n.equals("max") || n.equals("maxqty") || n.equals("maxstock")) {
            return "maximum";
        }
        if (n.equals("location") || n.equals("bin") || n.equals("shelflocation") || n.equals("aisle")
                || n.equals("shelf") || n.equals("shelfbinlocation") || n.equals("binlocation")) {
            return "location";
        }
        if (n.equals("description") || n.equals("desc") || n.equals("details")) {
            return "description";
        }
        if (n.equals("remarks") || n.equals("notes") || n.equals("comment") || n.equals("comments")) {
            return "remarks";
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
        if (n.equals("criticality") || n.equals("critical") || n.equals("priority")) {
            return "criticality";
        }
        if (n.equals("hsn") || n.equals("hsncode") || n.equals("tariff") || n.equals("tariffcode")
                || n.equals("hsntariffcode")) {
            return "hsnCode";
        }
        if (n.equals("vatapplicable") || n.equals("vat") || n.equals("taxable")) {
            return "vatApplicable";
        }
        if (n.equals("leadtime") || n.equals("leadtimedays") || n.equals("leadtimeday")) {
            return "leadTimeDays";
        }
        if (n.equals("weight") || n.equals("weightkg") || n.equals("wt") || n.equals("wtkg")) {
            return "weightKg";
        }
        if (n.equals("dimensions") || n.equals("dimension") || n.equals("lxwxh") || n.equals("size")) {
            return "dimensions";
        }
        if (n.equals("warranty") || n.equals("warrantymonths") || n.equals("warrantyperiod")) {
            return "warrantyMonths";
        }
        if (n.contains("supplier") && n.contains("part")) {
            return "supplierPartNo";
        }
        if (n.contains("warehouse") || (n.contains("default") && n.contains("wh"))) {
            return "warehouse";
        }
        if (n.contains("supplier") || n.contains("vendor")) {
            return "preferredSupplier";
        }
        if (n.contains("sub") && n.contains("categor")) {
            return "subCategory";
        }
        if (n.contains("categor")) {
            return "category";
        }
        if (n.contains("hsn") || n.contains("tariff")) {
            return "hsnCode";
        }
        if (n.contains("lead")) {
            return "leadTimeDays";
        }
        if (n.contains("warranty")) {
            return "warrantyMonths";
        }
        if (n.contains("weight")) {
            return "weightKg";
        }
        if (n.contains("dimension")) {
            return "dimensions";
        }
        if (n.contains("critical")) {
            return "criticality";
        }
        if (n.contains("reorder") && n.contains("qty")) {
            return "reorderQty";
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
