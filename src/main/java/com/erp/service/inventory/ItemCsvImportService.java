package com.erp.service.inventory;

import com.erp.domain.inventory.Category;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemCsvImportResultDTO;
import com.erp.dto.inventory.ItemCsvPreviewDTO;
import com.erp.repo.inventory.CategoryRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.inventory.ItemCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * CSV bulk import for inventory items with preview + confirm mapping.
 * Category / warehouse / supplier are resolved when possible; otherwise left empty
 * (warehouse falls back to company default because it is required).
 */
@Service
public class ItemCsvImportService {

    private final ItemService itemService;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final VendorRepository vendorRepo;
    private final CategoryRepository categoryRepo;
    private final AuthContext auth;
    private final ItemCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public ItemCsvImportService(
            ItemService itemService,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            VendorRepository vendorRepo,
            CategoryRepository categoryRepo,
            AuthContext auth,
            ItemCsvColumnMapperService columnMapper,
            ObjectMapper objectMapper
    ) {
        this.itemService = itemService;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.vendorRepo = vendorRepo;
        this.categoryRepo = categoryRepo;
        this.auth = auth;
        this.columnMapper = columnMapper;
        this.objectMapper = objectMapper;
    }

    public ItemCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mappingResult = columnMapper.mapHeaders(csv.headers(), csv.samples());
        Map<String, String> mapping = mappingResult.mapping();
        List<String> warnings = new ArrayList<>();
        if (!hasAny(mapping, "sku", "name")) {
            warnings.add("Could not map SKU or name — check column headers");
        }
        for (String header : csv.headers()) {
            if (ItemCsvColumnMapperService.isArabicHeader(header) && mapping.get(header) != null) {
                warnings.add("Arabic column should be ignored: " + header);
            }
        }
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples()) {
            sampleMapped.add(extractCanonicalValues(csv.headers(), row, mapping));
        }
        return ItemCsvPreviewDTO.builder()
                .headers(csv.headers())
                .fieldMapping(mapping)
                .aiMapped(mappingResult.aiMapped())
                .dataRowCount(csv.rows().size())
                .sampleRows(sampleMapped)
                .warnings(warnings)
                .build();
    }

    public ItemCsvImportResultDTO importCsv(MultipartFile file) {
        return importCsv(file, null);
    }

    public ItemCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);

        Map<String, String> fieldMapping;
        boolean aiMapped;
        if (mappingJson != null && !mappingJson.isBlank()) {
            fieldMapping = parseClientMapping(mappingJson, csv.headers());
            aiMapped = false;
        } else {
            MappingResult mappingResult = columnMapper.mapHeaders(csv.headers(), csv.samples());
            fieldMapping = mappingResult.mapping();
            aiMapped = mappingResult.aiMapped();
        }

        if (!hasAny(fieldMapping, "sku", "name")) {
            throw new IllegalArgumentException(
                    "Could not map required columns (need at least sku or name). "
                            + "Detected headers: " + String.join(", ", csv.headers()));
        }

        Long companyId = auth.getCurrentCompanyId();
        List<Warehouse> warehouses = warehouseRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        if (warehouses.isEmpty()) {
            throw new IllegalArgumentException("Create at least one warehouse before importing items");
        }
        Map<String, Warehouse> warehouseByName = new HashMap<>();
        Map<String, Warehouse> warehouseByCode = new HashMap<>();
        Map<String, Warehouse> warehouseById = new HashMap<>();
        for (Warehouse wh : warehouses) {
            if (wh.getName() != null) {
                warehouseByName.put(wh.getName().trim().toLowerCase(Locale.ROOT), wh);
            }
            if (wh.getCode() != null) {
                warehouseByCode.put(wh.getCode().trim().toLowerCase(Locale.ROOT), wh);
            }
            warehouseById.put(String.valueOf(wh.getId()), wh);
        }
        Warehouse defaultWarehouse = warehouses.get(0);

        Map<String, Vendor> vendorByName = new HashMap<>();
        Map<String, Vendor> vendorByCode = new HashMap<>();
        for (Vendor v : vendorRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (v.getVendorName() != null) {
                vendorByName.put(v.getVendorName().trim().toLowerCase(Locale.ROOT), v);
            }
            if (v.getVendorCode() != null) {
                vendorByCode.put(v.getVendorCode().trim().toLowerCase(Locale.ROOT), v);
            }
        }

        Map<String, Category> parentCategories = new HashMap<>();
        for (Category c : categoryRepo.findByCompanyIdAndParentIdIsNullOrderByCreatedAtDesc(companyId)) {
            if (c.getName() != null) {
                parentCategories.put(c.getName().trim().toLowerCase(Locale.ROOT), c);
            }
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<ItemCsvImportResultDTO.RowError> errors = new ArrayList<>();

        int rowNum = 1;
        for (List<String> cols : csv.rows()) {
            rowNum++;
            String sku = null;
            try {
                Map<String, String> values = extractCanonicalValues(csv.headers(), cols, fieldMapping);
                Map<String, String> metadata = extractMetadata(csv.headers(), cols, fieldMapping);

                sku = blankToNull(values.get("sku"));
                String name = blankToNull(values.get("name"));
                if (sku == null && name == null) {
                    throw new IllegalArgumentException("SKU or name is required");
                }
                if (sku == null) {
                    sku = synthesizeSku(name, rowNum);
                }
                sku = sku.trim().toUpperCase(Locale.ROOT);
                if (name == null) {
                    name = sku;
                }

                if (itemRepo.existsBySkuAndCompanyId(sku, companyId)) {
                    skipped++;
                    errors.add(ItemCsvImportResultDTO.RowError.builder()
                            .row(rowNum).sku(sku).message("SKU already exists — skipped").build());
                    continue;
                }

                String categoryRaw = blankToNull(values.get("category"));
                String subRaw = blankToNull(values.get("subCategory"));
                String category = resolveCategoryName(categoryRaw, parentCategories);
                String subCategory = resolveSubCategoryName(category, subRaw, companyId);

                Warehouse warehouse = resolveWarehouse(
                        values.get("warehouse"), warehouseById, warehouseByCode, warehouseByName, defaultWarehouse);
                Vendor preferred = resolveVendor(values.get("preferredSupplier"), vendorByCode, vendorByName);

                Integer quantity = parseOptionalInt(values.get("quantity"));
                if (quantity == null) quantity = 0;
                if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");

                ItemCreateDTO dto = new ItemCreateDTO();
                dto.setSku(sku);
                dto.setName(name.trim());
                dto.setCategory(category);
                dto.setSubCategory(subCategory);
                dto.setWarehouse(warehouse.getId());
                dto.setPreferredVendorId(preferred != null ? preferred.getId() : null);
                dto.setQuantity(quantity);
                dto.setUnitMeasure(blankToDefault(values.get("unitMeasure"), "pcs"));
                dto.setBarcode(blankToNull(values.get("barcode")));
                dto.setBrand(blankToNull(values.get("brand")));
                dto.setType(normalizeItemType(values.get("type")));
                dto.setModel(blankToNull(values.get("model")));
                dto.setManufacturerPartNumber(blankToNull(values.get("manufacturerPartNumber")));
                dto.setLocation(blankToNull(values.get("location")));
                dto.setDescription(blankToNull(values.get("description")));
                dto.setRemarks(blankToNull(values.get("remarks")));
                dto.setSerialNo(blankToNull(values.get("serialNo")));
                dto.setDateReceived(blankToNull(values.get("dateReceived")));
                dto.setExpiryDate(blankToNull(values.get("expiryDate")));
                dto.setStatus(blankToDefault(values.get("status"), "active").toLowerCase(Locale.ROOT));
                dto.setCostPrice(parseOptionalDecimal(values.get("costPrice")));
                dto.setSellingPrice(parseOptionalDecimal(values.get("sellingPrice")));
                dto.setReorderLevel(parseOptionalInt(values.get("reorderLevel")));
                dto.setReorderQty(parseOptionalInt(values.get("reorderQty")));
                dto.setMinimum(parseOptionalInt(values.get("minimum")));
                dto.setMaximum(parseOptionalInt(values.get("maximum")));
                dto.setCriticality(blankToNull(values.get("criticality")));
                dto.setHsnCode(blankToNull(values.get("hsnCode")));
                dto.setVatApplicable(parseOptionalBoolean(values.get("vatApplicable")));
                dto.setLeadTimeDays(parseOptionalInt(values.get("leadTimeDays")));
                dto.setSupplierPartNo(blankToNull(values.get("supplierPartNo")));
                dto.setWeightKg(parseOptionalDouble(values.get("weightKg")));
                dto.setDimensions(blankToNull(values.get("dimensions")));
                dto.setWarrantyMonths(parseOptionalInt(values.get("warrantyMonths")));
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

        return ItemCsvImportResultDTO.builder()
                .created(created)
                .skipped(skipped)
                .failed(failed)
                .fieldMapping(fieldMapping)
                .aiMapped(aiMapped)
                .errors(errors)
                .build();
    }

    private String resolveCategoryName(String raw, Map<String, Category> parents) {
        if (raw == null) return null;
        Category match = parents.get(raw.trim().toLowerCase(Locale.ROOT));
        return match != null ? match.getName() : null;
    }

    private String resolveSubCategoryName(String parentName, String raw, Long companyId) {
        if (raw == null) return null;
        if (parentName == null) {
            // No parent mapped — leave empty rather than orphan string
            return null;
        }
        Optional<Category> parent = categoryRepo.findByCompanyIdAndParentIdIsNullAndNameIgnoreCase(
                companyId, parentName);
        if (parent.isEmpty()) return null;
        return categoryRepo.findByCompanyIdAndParentIdAndNameIgnoreCase(
                        companyId, parent.get().getId(), raw.trim())
                .map(Category::getName)
                .orElse(null);
    }

    private static Warehouse resolveWarehouse(
            String warehouseRaw,
            Map<String, Warehouse> byId,
            Map<String, Warehouse> byCode,
            Map<String, Warehouse> byName,
            Warehouse defaultWarehouse
    ) {
        if (warehouseRaw == null || warehouseRaw.isBlank()) {
            return defaultWarehouse;
        }
        String raw = warehouseRaw.trim();
        Warehouse warehouse = byId.get(raw);
        if (warehouse == null) {
            // "WH-001 Main Store" → try code prefix
            String lower = raw.toLowerCase(Locale.ROOT);
            warehouse = byCode.get(lower);
            if (warehouse == null) {
                String codeToken = lower.split("\\s+")[0];
                warehouse = byCode.get(codeToken);
            }
            if (warehouse == null) {
                warehouse = byName.get(lower);
            }
            if (warehouse == null) {
                for (Map.Entry<String, Warehouse> e : byName.entrySet()) {
                    if (lower.contains(e.getKey()) || e.getKey().contains(lower)) {
                        warehouse = e.getValue();
                        break;
                    }
                }
            }
        }
        return warehouse != null ? warehouse : defaultWarehouse;
    }

    private static Vendor resolveVendor(
            String raw,
            Map<String, Vendor> byCode,
            Map<String, Vendor> byName
    ) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        Vendor v = byCode.get(lower);
        if (v != null) return v;
        v = byName.get(lower);
        if (v != null) return v;
        for (Map.Entry<String, Vendor> e : byName.entrySet()) {
            if (lower.contains(e.getKey()) || e.getKey().contains(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String normalizeItemType(String raw) {
        if (raw == null || raw.isBlank()) return "Stock";
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if (n.contains("consum")) return "Consumable";
        if (n.contains("asset")) return "Asset";
        if (n.contains("stock") || n.contains("product")) return "Stock";
        return raw.trim();
    }

    private Map<String, String> parseClientMapping(String mappingJson, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    mappingJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> cleaned = new LinkedHashMap<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (String header : headers) {
                if (ItemCsvColumnMapperService.isArabicHeader(header)) {
                    cleaned.put(header, null);
                    continue;
                }
                String target = raw.get(header);
                if (target == null || target.isBlank() || "null".equalsIgnoreCase(target)
                        || "ignore".equalsIgnoreCase(target) || "metadata".equalsIgnoreCase(target)) {
                    cleaned.put(header, null);
                    continue;
                }
                String canonical = null;
                for (String field : ItemCsvColumnMapperService.CANONICAL_FIELDS) {
                    if (field.equalsIgnoreCase(target.trim())) {
                        canonical = field;
                        break;
                    }
                }
                if (canonical == null || !used.add(canonical)) {
                    cleaned.put(header, null);
                } else {
                    cleaned.put(header, canonical);
                }
            }
            return cleaned;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid mapping JSON: " + ex.getMessage());
        }
    }

    private ParsedCsv parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IllegalArgumentException("CSV file is empty");
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = parseCsvLine(headerLine);
            if (headers.isEmpty() || headers.stream().allMatch(h -> h == null || h.isBlank())) {
                throw new IllegalArgumentException("CSV header row is empty");
            }
            List<List<String>> allRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) allRows.add(parseCsvLine(line));
            }
            return new ParsedCsv(headers, allRows, allRows.stream().limit(3).toList());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read CSV: " + ex.getMessage(), ex);
        }
    }

    private static boolean hasAny(Map<String, String> mapping, String... fields) {
        for (String field : fields) {
            if (mapping.containsValue(field)) return true;
        }
        return false;
    }

    private static Map<String, String> extractCanonicalValues(
            List<String> headers, List<String> cols, Map<String, String> fieldMapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String canonical = fieldMapping.get(headers.get(i));
            if (canonical == null || canonical.isBlank() || values.containsKey(canonical)) continue;
            values.put(canonical, i < cols.size() ? cols.get(i) : null);
        }
        return values;
    }

    private static Map<String, String> extractMetadata(
            List<String> headers, List<String> cols, Map<String, String> fieldMapping) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.isBlank()) continue;
            if (ItemCsvColumnMapperService.isArabicHeader(header)) continue;
            String canonical = fieldMapping.get(header);
            if (canonical != null && !canonical.isBlank()) continue;
            String value = i < cols.size() ? cols.get(i) : null;
            if (value == null || value.isBlank()) continue;
            metadata.put(header.trim(), value.trim());
        }
        return metadata;
    }

    private static String synthesizeSku(String name, int rowNum) {
        String base = name == null ? "ITEM" : name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.length() > 20) base = base.substring(0, 20);
        if (base.isBlank()) base = "ITEM";
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
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        String cleaned = blankToNull(value);
        return cleaned != null ? cleaned : defaultValue;
    }

    private static Integer parseOptionalInt(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) return null;
        String numeric = cleaned.replace(",", "").replaceAll("[^0-9\\-]", "");
        if (numeric.isBlank() || numeric.equals("-")) return null;
        return Integer.parseInt(numeric);
    }

    private static Double parseOptionalDouble(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) return null;
        String numeric = cleaned.replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (numeric.isBlank() || numeric.equals("-") || numeric.equals(".")) return null;
        return Double.parseDouble(numeric);
    }

    private static BigDecimal parseOptionalDecimal(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) return null;
        return new BigDecimal(cleaned.replace(",", "").replaceAll("[^0-9.\\-]", ""));
    }

    private static Boolean parseOptionalBoolean(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) return null;
        String n = cleaned.toLowerCase(Locale.ROOT);
        if (n.equals("yes") || n.equals("y") || n.equals("true") || n.equals("1")) return true;
        if (n.equals("no") || n.equals("n") || n.equals("false") || n.equals("0")) return false;
        return null;
    }

    private record ParsedCsv(List<String> headers, List<List<String>> rows, List<List<String>> samples) {}
}
