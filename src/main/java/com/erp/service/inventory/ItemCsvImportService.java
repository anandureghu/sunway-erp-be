package com.erp.service.inventory;

import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemCsvImportResultDTO;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.inventory.ItemCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV bulk import for inventory items. Accepts arbitrary client spreadsheet headers;
 * OpenAI (when configured) maps them onto canonical fields, with leftovers stored in
 * {@code Item.metadata}. Each successful create commits independently.
 */
@Service
public class ItemCsvImportService {

    private final ItemService itemService;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final AuthContext auth;
    private final ItemCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public ItemCsvImportService(
            ItemService itemService,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            AuthContext auth,
            ItemCsvColumnMapperService columnMapper,
            ObjectMapper objectMapper
    ) {
        this.itemService = itemService;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.auth = auth;
        this.columnMapper = columnMapper;
        this.objectMapper = objectMapper;
    }

    public ItemCsvImportResultDTO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        Long companyId = auth.getCurrentCompanyId();
        List<Warehouse> warehouses = warehouseRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        if (warehouses.isEmpty()) {
            throw new IllegalArgumentException("Create at least one warehouse before importing items");
        }
        Map<String, Warehouse> warehouseByName = new HashMap<>();
        Map<String, Warehouse> warehouseById = new HashMap<>();
        for (Warehouse wh : warehouses) {
            if (wh.getName() != null) {
                warehouseByName.put(wh.getName().trim().toLowerCase(), wh);
            }
            warehouseById.put(String.valueOf(wh.getId()), wh);
        }
        Warehouse defaultWarehouse = warehouses.get(0);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<ItemCsvImportResultDTO.RowError> errors = new ArrayList<>();
        Map<String, String> fieldMapping = Map.of();
        boolean aiMapped = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);
            if (headers.isEmpty() || headers.stream().allMatch(h -> h == null || h.isBlank())) {
                throw new IllegalArgumentException("CSV header row is empty");
            }

            // Buffer a few sample + all data rows so we can map headers with AI first.
            List<List<String>> allRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                allRows.add(parseCsvLine(line));
            }

            List<List<String>> samples = allRows.stream().limit(3).toList();
            MappingResult mappingResult = columnMapper.mapHeaders(headers, samples);
            fieldMapping = mappingResult.mapping();
            aiMapped = mappingResult.aiMapped();

            Map<String, Integer> canonicalIndex = buildCanonicalIndex(headers, fieldMapping);
            if (!canonicalIndex.containsKey("sku") && !canonicalIndex.containsKey("name")) {
                throw new IllegalArgumentException(
                        "Could not map required columns (need at least sku or name). "
                                + "Detected headers: " + String.join(", ", headers));
            }

            int rowNum = 1;
            for (List<String> cols : allRows) {
                rowNum++;
                String sku = null;
                try {
                    Map<String, String> valuesByField = extractCanonicalValues(headers, cols, fieldMapping);
                    Map<String, String> metadata = extractMetadata(headers, cols, fieldMapping);

                    sku = blankToNull(valuesByField.get("sku"));
                    String name = blankToNull(valuesByField.get("name"));
                    if (sku == null && name == null) {
                        throw new IllegalArgumentException("SKU or name is required");
                    }
                    if (sku == null) {
                        sku = synthesizeSku(name, rowNum);
                    }
                    sku = sku.trim().toUpperCase();
                    if (name == null) {
                        name = sku;
                    }

                    if (itemRepo.existsBySkuAndCompanyId(sku, companyId)) {
                        skipped++;
                        errors.add(ItemCsvImportResultDTO.RowError.builder()
                                .row(rowNum)
                                .sku(sku)
                                .message("SKU already exists — skipped")
                                .build());
                        continue;
                    }

                    String category = blankToDefault(valuesByField.get("category"), "General");
                    Warehouse warehouse = resolveWarehouse(
                            valuesByField.get("warehouse"), warehouseById, warehouseByName, defaultWarehouse);

                    Integer quantity = parseOptionalInt(valuesByField.get("quantity"));
                    if (quantity == null) {
                        quantity = 0;
                    }
                    if (quantity < 0) {
                        throw new IllegalArgumentException("Quantity cannot be negative");
                    }

                    ItemCreateDTO dto = new ItemCreateDTO();
                    dto.setSku(sku);
                    dto.setName(name.trim());
                    dto.setCategory(category.trim());
                    dto.setWarehouse(warehouse.getId());
                    dto.setQuantity(quantity);
                    dto.setUnitMeasure(blankToDefault(valuesByField.get("unitMeasure"), "pcs"));
                    dto.setBarcode(blankToNull(valuesByField.get("barcode")));
                    dto.setBrand(blankToNull(valuesByField.get("brand")));
                    dto.setType(blankToDefault(valuesByField.get("type"), "product"));
                    dto.setSubCategory(blankToNull(valuesByField.get("subCategory")));
                    dto.setLocation(blankToNull(valuesByField.get("location")));
                    dto.setDescription(blankToNull(valuesByField.get("description")));
                    dto.setSerialNo(blankToNull(valuesByField.get("serialNo")));
                    dto.setDateReceived(blankToNull(valuesByField.get("dateReceived")));
                    dto.setExpiryDate(blankToNull(valuesByField.get("expiryDate")));
                    dto.setStatus(blankToDefault(valuesByField.get("status"), "active"));
                    dto.setCostPrice(parseOptionalDecimal(valuesByField.get("costPrice")));
                    dto.setSellingPrice(parseOptionalDecimal(valuesByField.get("sellingPrice")));
                    dto.setReorderLevel(parseOptionalInt(valuesByField.get("reorderLevel")));
                    dto.setMinimum(parseOptionalInt(valuesByField.get("minimum")));
                    dto.setMaximum(parseOptionalInt(valuesByField.get("maximum")));
                    if (!metadata.isEmpty()) {
                        dto.setMetadata(objectMapper.writeValueAsString(metadata));
                    }

                    itemService.create(dto, null);
                    created++;
                } catch (Exception ex) {
                    failed++;
                    errors.add(ItemCsvImportResultDTO.RowError.builder()
                            .row(rowNum)
                            .sku(sku)
                            .message(ex.getMessage() != null ? ex.getMessage() : "Import failed")
                            .build());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read CSV: " + ex.getMessage(), ex);
        }

        return ItemCsvImportResultDTO.builder()
                .created(created)
                .skipped(skipped)
                .failed(failed)
                .fieldMapping(fieldMapping)
                .aiMapped(aiMapped)
                .errors(errors)
                .build();
    }

    private static Map<String, Integer> buildCanonicalIndex(
            List<String> headers, Map<String, String> fieldMapping) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String canonical = fieldMapping.get(header);
            if (canonical != null && !canonical.isBlank() && !index.containsKey(canonical)) {
                index.put(canonical, i);
            }
        }
        return index;
    }

    private static Map<String, String> extractCanonicalValues(
            List<String> headers, List<String> cols, Map<String, String> fieldMapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String canonical = fieldMapping.get(header);
            if (canonical == null || canonical.isBlank()) {
                continue;
            }
            if (values.containsKey(canonical)) {
                continue;
            }
            String value = i < cols.size() ? cols.get(i) : null;
            values.put(canonical, value);
        }
        return values;
    }

    private static Map<String, String> extractMetadata(
            List<String> headers, List<String> cols, Map<String, String> fieldMapping) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.isBlank()) {
                continue;
            }
            String canonical = fieldMapping.get(header);
            if (canonical != null && !canonical.isBlank()) {
                continue;
            }
            String value = i < cols.size() ? cols.get(i) : null;
            if (value == null || value.isBlank()) {
                continue;
            }
            metadata.put(header.trim(), value.trim());
        }
        return metadata;
    }

    private static Warehouse resolveWarehouse(
            String warehouseRaw,
            Map<String, Warehouse> byId,
            Map<String, Warehouse> byName,
            Warehouse defaultWarehouse
    ) {
        if (warehouseRaw == null || warehouseRaw.isBlank()) {
            return defaultWarehouse;
        }
        Warehouse warehouse = byId.get(warehouseRaw.trim());
        if (warehouse == null) {
            warehouse = byName.get(warehouseRaw.trim().toLowerCase());
        }
        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse not found: " + warehouseRaw);
        }
        return warehouse;
    }

    private static String synthesizeSku(String name, int rowNum) {
        String base = name == null ? "ITEM" : name.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        if (base.isBlank()) {
            base = "ITEM";
        }
        return base + "-" + rowNum;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        String cleaned = blankToNull(value);
        return cleaned != null ? cleaned : defaultValue;
    }

    private static Integer parseOptionalInt(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        return Integer.parseInt(cleaned.replace(",", "").replaceAll("[^0-9\\-]", ""));
    }

    private static BigDecimal parseOptionalDecimal(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        return new BigDecimal(cleaned.replace(",", "").replaceAll("[^0-9.\\-]", ""));
    }
}
