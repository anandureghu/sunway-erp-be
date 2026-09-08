package com.erp.service.inventory;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.WarehouseCsvImportResultDTO;
import com.erp.dto.inventory.WarehouseCsvPreviewDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.service.inventory.WarehouseCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bulk import warehouses from CSV. Preview maps headers; confirm imports with optional overrides.
 */
@Service
public class WarehouseCsvImportService {

    private final WarehouseRepository warehouseRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final AuthContext auth;
    private final WarehouseCsvColumnMapperService columnMapper;
    private final DocumentSequenceService documentSequenceService;
    private final ObjectMapper objectMapper;

    public WarehouseCsvImportService(
            WarehouseRepository warehouseRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            EmployeeRepository employeeRepo,
            AuthContext auth,
            WarehouseCsvColumnMapperService columnMapper,
            DocumentSequenceService documentSequenceService,
            ObjectMapper objectMapper
    ) {
        this.warehouseRepo = warehouseRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.auth = auth;
        this.columnMapper = columnMapper;
        this.documentSequenceService = documentSequenceService;
        this.objectMapper = objectMapper;
    }

    public WarehouseCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mappingResult = columnMapper.mapHeaders(csv.headers(), csv.samples());
        Map<String, String> mapping = mappingResult.mapping();

        List<String> warnings = new ArrayList<>();
        if (!hasAny(mapping, "warehouseCode", "warehouseName")) {
            warnings.add("Could not map warehouse code or name — check column headers");
        }
        for (String header : csv.headers()) {
            if (WarehouseCsvColumnMapperService.isArabicHeader(header)
                    && mapping.get(header) != null) {
                warnings.add("Arabic column should be ignored: " + header);
            }
        }

        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples()) {
            sampleMapped.add(extractCanonicalValues(csv.headers(), row, mapping));
        }

        return WarehouseCsvPreviewDTO.builder()
                .headers(csv.headers())
                .fieldMapping(mapping)
                .aiMapped(mappingResult.aiMapped())
                .dataRowCount(csv.rows().size())
                .sampleRows(sampleMapped)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public WarehouseCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
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

        if (!hasAny(fieldMapping, "warehouseCode", "warehouseName")) {
            throw new IllegalArgumentException(
                    "Could not map required columns (need warehouse code or name). "
                            + "Detected headers: " + String.join(", ", csv.headers()));
        }

        Long companyId = auth.getCurrentCompanyId();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        User actor = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, User> managersByName = buildManagerNameIndex(companyId);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<WarehouseCsvImportResultDTO.RowError> errors = new ArrayList<>();

        int rowNum = 1;
        for (List<String> cols : csv.rows()) {
            rowNum++;
            String rowCode = null;
            try {
                Map<String, String> values = extractCanonicalValues(csv.headers(), cols, fieldMapping);

                String code = blankToNull(values.get("warehouseCode"));
                String name = blankToNull(values.get("warehouseName"));
                if (code == null && name == null) {
                    throw new IllegalArgumentException("Warehouse code or name is required");
                }
                if (name == null) {
                    name = code;
                }
                rowCode = code;

                if (code != null) {
                    Optional<Warehouse> existing = warehouseRepo.findByCompanyIdAndCodeIgnoreCase(
                            companyId, code);
                    if (existing.isPresent()) {
                        skipped++;
                        errors.add(WarehouseCsvImportResultDTO.RowError.builder()
                                .row(rowNum)
                                .code(code)
                                .message("Warehouse code already exists — skipped")
                                .build());
                        continue;
                    }
                }

                String resolvedCode = code != null ? code.trim() : documentSequenceService.generateNext("WH");
                if (warehouseRepo.existsByCodeAndCompanyId(resolvedCode, companyId)) {
                    skipped++;
                    errors.add(WarehouseCsvImportResultDTO.RowError.builder()
                            .row(rowNum)
                            .code(resolvedCode)
                            .message("Warehouse code already exists — skipped")
                            .build());
                    continue;
                }

                String managerRaw = blankToNull(values.get("manager"));
                User manager = resolveManagerByName(managerRaw, managersByName);

                Warehouse wh = Warehouse.builder()
                        .code(resolvedCode)
                        .name(name.trim())
                        .warehouseType(WarehouseService.normalizeWarehouseType(values.get("warehouseType")))
                        .capacity(parseOptionalDouble(values.get("capacity")))
                        .status(WarehouseService.normalizeStatus(values.get("status")))
                        .street(blankToNull(values.get("street")))
                        .city(blankToNull(values.get("city")))
                        .country(blankToNull(values.get("country")))
                        .pin(blankToNull(values.get("pin")))
                        .phone(blankToNull(values.get("phone")))
                        .contactPersonName(blankToNull(values.get("contactPersonName")))
                        .manager(manager)
                        .company(company)
                        .createdByUser(actor)
                        .updatedByUser(actor)
                        .build();

                // If manager name didn't resolve to a user, keep the name as contact person when empty
                if (manager == null && managerRaw != null
                        && (wh.getContactPersonName() == null || wh.getContactPersonName().isBlank())) {
                    wh.setContactPersonName(managerRaw);
                }

                warehouseRepo.save(wh);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(WarehouseCsvImportResultDTO.RowError.builder()
                        .row(rowNum)
                        .code(rowCode)
                        .message(ex.getMessage() != null ? ex.getMessage() : "Import failed")
                        .build());
            }
        }

        return WarehouseCsvImportResultDTO.builder()
                .created(created)
                .skipped(skipped)
                .failed(failed)
                .fieldMapping(fieldMapping)
                .aiMapped(aiMapped)
                .errors(errors)
                .build();
    }

    private Map<String, User> buildManagerNameIndex(Long companyId) {
        Map<String, User> index = new HashMap<>();
        List<Employee> employees = employeeRepo.findByCompany_IdOrderByCreatedAtDesc(companyId);
        for (Employee emp : employees) {
            User user = emp.getUser();
            if (user == null) {
                continue;
            }
            String full = user.getFullName();
            if (full != null && !full.isBlank()) {
                index.putIfAbsent(full.trim().toLowerCase(Locale.ROOT), user);
            }
        }
        return index;
    }

    private static User resolveManagerByName(String raw, Map<String, User> byName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return byName.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    private Map<String, String> parseClientMapping(String mappingJson, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    mappingJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> cleaned = new LinkedHashMap<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (String header : headers) {
                if (WarehouseCsvColumnMapperService.isArabicHeader(header)) {
                    cleaned.put(header, null);
                    continue;
                }
                String target = raw.get(header);
                if (target == null || target.isBlank() || "null".equalsIgnoreCase(target)
                        || "ignore".equalsIgnoreCase(target)) {
                    cleaned.put(header, null);
                    continue;
                }
                String canonical = null;
                for (String field : WarehouseCsvColumnMapperService.CANONICAL_FIELDS) {
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

            List<List<String>> allRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                allRows.add(parseCsvLine(line));
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
            if (mapping.containsValue(field)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> extractCanonicalValues(
            List<String> headers, List<String> cols, Map<String, String> fieldMapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String canonical = fieldMapping.get(header);
            if (canonical == null || canonical.isBlank() || values.containsKey(canonical)) {
                continue;
            }
            values.put(canonical, i < cols.size() ? cols.get(i) : null);
        }
        return values;
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

    private static Double parseOptionalDouble(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        String numeric = cleaned.replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (numeric.isBlank() || numeric.equals("-") || numeric.equals(".")) {
            return null;
        }
        return Double.parseDouble(numeric);
    }

    private record ParsedCsv(List<String> headers, List<List<String>> rows, List<List<String>> samples) {}
}
