package com.erp.service.inventory;

import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemCsvImportResultDTO;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV bulk import for inventory items. Lives outside {@link ItemService} so each
 * successful {@code create} commits independently — a bad row does not roll back prior rows.
 */
@Service
public class ItemCsvImportService {

    private final ItemService itemService;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final AuthContext auth;

    public ItemCsvImportService(
            ItemService itemService,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            AuthContext auth
    ) {
        this.itemService = itemService;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.auth = auth;
    }

    /**
     * Bulk-create inventory items from a CSV upload. Expected headers (case-insensitive):
     * sku, name, category, warehouse (id or name), quantity, unitMeasure, and optional
     * barcode, brand, type, subCategory, costPrice, sellingPrice, status, reorderLevel,
     * minimum, maximum, location, description.
     * Duplicate SKUs within the company are skipped.
     */
    public ItemCsvImportResultDTO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        Long companyId = auth.getCurrentCompanyId();
        List<Warehouse> warehouses = warehouseRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<String, Warehouse> warehouseByName = new HashMap<>();
        Map<String, Warehouse> warehouseById = new HashMap<>();
        for (Warehouse wh : warehouses) {
            if (wh.getName() != null) {
                warehouseByName.put(wh.getName().trim().toLowerCase(), wh);
            }
            warehouseById.put(String.valueOf(wh.getId()), wh);
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<ItemCsvImportResultDTO.RowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }

            Map<String, Integer> headerIndex = parseCsvHeader(headerLine);
            requireHeader(headerIndex, "sku");
            requireHeader(headerIndex, "name");
            requireHeader(headerIndex, "category");
            requireHeader(headerIndex, "warehouse");

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                String sku = cell(cols, headerIndex, "sku");
                try {
                    if (sku == null || sku.isBlank()) {
                        throw new IllegalArgumentException("SKU is required");
                    }
                    sku = sku.trim().toUpperCase();
                    if (itemRepo.existsBySkuAndCompanyId(sku, companyId)) {
                        skipped++;
                        errors.add(ItemCsvImportResultDTO.RowError.builder()
                                .row(rowNum)
                                .sku(sku)
                                .message("SKU already exists — skipped")
                                .build());
                        continue;
                    }

                    String name = cell(cols, headerIndex, "name");
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Name is required");
                    }
                    String category = cell(cols, headerIndex, "category");
                    if (category == null || category.isBlank()) {
                        throw new IllegalArgumentException("Category is required");
                    }

                    String warehouseRaw = cell(cols, headerIndex, "warehouse");
                    if (warehouseRaw == null || warehouseRaw.isBlank()) {
                        throw new IllegalArgumentException("Warehouse is required (id or name)");
                    }
                    Warehouse warehouse = warehouseById.get(warehouseRaw.trim());
                    if (warehouse == null) {
                        warehouse = warehouseByName.get(warehouseRaw.trim().toLowerCase());
                    }
                    if (warehouse == null) {
                        throw new IllegalArgumentException("Warehouse not found: " + warehouseRaw);
                    }

                    Integer quantity = parseOptionalInt(cell(cols, headerIndex, "quantity"));
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
                    dto.setUnitMeasure(blankToDefault(cell(cols, headerIndex, "unitmeasure"), "pcs"));
                    dto.setBarcode(blankToNull(cell(cols, headerIndex, "barcode")));
                    dto.setBrand(blankToNull(cell(cols, headerIndex, "brand")));
                    dto.setType(blankToDefault(cell(cols, headerIndex, "type"), "product"));
                    dto.setSubCategory(blankToNull(cell(cols, headerIndex, "subcategory")));
                    dto.setLocation(blankToNull(cell(cols, headerIndex, "location")));
                    dto.setDescription(blankToNull(cell(cols, headerIndex, "description")));
                    dto.setStatus(blankToDefault(cell(cols, headerIndex, "status"), "active"));
                    dto.setCostPrice(parseOptionalDecimal(cell(cols, headerIndex, "costprice")));
                    dto.setSellingPrice(parseOptionalDecimal(cell(cols, headerIndex, "sellingprice")));
                    dto.setReorderLevel(parseOptionalInt(cell(cols, headerIndex, "reorderlevel")));
                    dto.setMinimum(parseOptionalInt(cell(cols, headerIndex, "minimum")));
                    dto.setMaximum(parseOptionalInt(cell(cols, headerIndex, "maximum")));

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
                .errors(errors)
                .build();
    }

    private static Map<String, Integer> parseCsvHeader(String headerLine) {
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = normalizeHeader(headers.get(i));
            if (!key.isEmpty()) {
                index.put(key, i);
            }
        }
        return index;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase()
                .replace(" ", "")
                .replace("_", "")
                .replace("/", "")
                .replace("-", "");
    }

    private static void requireHeader(Map<String, Integer> headerIndex, String key) {
        if (!headerIndex.containsKey(key)) {
            throw new IllegalArgumentException("Missing required CSV column: " + key);
        }
    }

    private static String cell(List<String> cols, Map<String, Integer> headerIndex, String key) {
        Integer idx = headerIndex.get(key);
        if (idx == null || idx < 0 || idx >= cols.size()) {
            return null;
        }
        return cols.get(idx);
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
        return Integer.parseInt(cleaned.replace(",", ""));
    }

    private static BigDecimal parseOptionalDecimal(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        return new BigDecimal(cleaned.replace(",", ""));
    }
}
