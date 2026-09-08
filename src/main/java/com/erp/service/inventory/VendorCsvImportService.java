package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Category;
import com.erp.domain.inventory.Vendor;
import com.erp.dto.inventory.VendorCsvImportResultDTO;
import com.erp.dto.inventory.VendorCsvPreviewDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CategoryRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.service.inventory.VendorCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class VendorCsvImportService {

    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("electrical", "Electrical"),
            Map.entry("electronics", "Electrical"),
            Map.entry("hvac", "HVAC"),
            Map.entry("fireandsafety", "Fire & Safety"),
            Map.entry("firesafety", "Fire & Safety"),
            Map.entry("multicategory", "Multi-Category"),
            Map.entry("multi", "Multi-Category"),
            Map.entry("ppe", "PPE"),
            Map.entry("personprotectiveequipment", "PPE"),
            Map.entry("security", "Security"),
            Map.entry("cleaning", "Cleaning"),
            Map.entry("elevators", "Elevators"),
            Map.entry("elevator", "Elevators")
    );

    private final VendorRepository vendorRepo;
    private final CategoryRepository categoryRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final VendorCsvColumnMapperService columnMapper;
    private final DocumentSequenceService documentSequenceService;
    private final ObjectMapper objectMapper;

    public VendorCsvImportService(
            VendorRepository vendorRepo,
            CategoryRepository categoryRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            VendorCsvColumnMapperService columnMapper,
            DocumentSequenceService documentSequenceService,
            ObjectMapper objectMapper
    ) {
        this.vendorRepo = vendorRepo;
        this.categoryRepo = categoryRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.columnMapper = columnMapper;
        this.documentSequenceService = documentSequenceService;
        this.objectMapper = objectMapper;
    }

    public VendorCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mappingResult = columnMapper.mapHeaders(csv.headers(), csv.samples());
        Map<String, String> mapping = mappingResult.mapping();

        List<String> warnings = new ArrayList<>();
        if (!hasAny(mapping, "vendorCode", "vendorName")) {
            warnings.add("Could not map supplier code or name — check column headers");
        }
        for (String header : csv.headers()) {
            if (VendorCsvColumnMapperService.isArabicHeader(header) && mapping.get(header) != null) {
                warnings.add("Arabic column should be ignored: " + header);
            }
        }

        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples()) {
            sampleMapped.add(extractCanonicalValues(csv.headers(), row, mapping));
        }

        return VendorCsvPreviewDTO.builder()
                .headers(csv.headers())
                .fieldMapping(mapping)
                .aiMapped(mappingResult.aiMapped())
                .dataRowCount(csv.rows().size())
                .sampleRows(sampleMapped)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public VendorCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
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

        if (!hasAny(fieldMapping, "vendorCode", "vendorName")) {
            throw new IllegalArgumentException(
                    "Could not map required columns (need supplier code or name). "
                            + "Detected headers: " + String.join(", ", csv.headers()));
        }

        Long companyId = auth.getCurrentCompanyId();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        User actor = userRepo.findById(auth.getCurrentUserId()).orElse(null);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<VendorCsvImportResultDTO.RowError> errors = new ArrayList<>();

        int rowNum = 1;
        for (List<String> cols : csv.rows()) {
            rowNum++;
            String rowCode = null;
            try {
                Map<String, String> values = extractCanonicalValues(csv.headers(), cols, fieldMapping);

                String code = blankToNull(values.get("vendorCode"));
                String name = blankToNull(values.get("vendorName"));
                if (code == null && name == null) {
                    throw new IllegalArgumentException("Supplier code or name is required");
                }
                if (name == null) {
                    name = code;
                }
                rowCode = code;

                if (code != null) {
                    Optional<Vendor> existing = vendorRepo.findByCompanyIdAndVendorCodeIgnoreCase(
                            companyId, code);
                    if (existing.isPresent()) {
                        skipped++;
                        errors.add(error(rowNum, code, "Supplier code already exists — skipped"));
                        continue;
                    }
                }

                String resolvedCode = code != null ? code.trim() : documentSequenceService.generateNext("SUP");
                if (vendorRepo.existsByVendorCodeAndCompanyId(resolvedCode, companyId)) {
                    skipped++;
                    errors.add(error(rowNum, resolvedCode, "Supplier code already exists — skipped"));
                    continue;
                }

                String taxId = blankToNull(values.get("taxId"));
                String paymentTerms = blankToNull(values.get("paymentTerms"));
                if (paymentTerms == null) {
                    paymentTerms = "Net 30";
                }
                String currency = blankToNull(values.get("currencyCode"));
                if (currency == null) {
                    currency = "QAR";
                }

                Category category = resolveOrCreateCategory(
                        values.get("categoryName"), company, actor);

                Vendor vendor = Vendor.builder()
                        .vendorCode(resolvedCode)
                        .vendorName(name.trim())
                        .taxId(taxId)
                        .category(category)
                        .vendorCrNo(blankToNull(values.get("vendorCrNo")))
                        .bankName(blankToNull(values.get("bankName")))
                        .iban(blankToNull(values.get("iban")))
                        .paymentTerms(paymentTerms)
                        .currencyCode(currency.toUpperCase(Locale.ROOT))
                        .creditLimit(parseOptionalDecimal(values.get("creditLimit")))
                        .is1099Vendor(taxId != null)
                        .isActive(normalizeActive(values.get("status")))
                        .street(blankToNull(values.get("street")))
                        .city(blankToNull(values.get("city")))
                        .country(blankToNull(values.get("country")))
                        .phoneNo(blankToNull(values.get("phoneNo")))
                        .email(blankToNull(values.get("email")))
                        .contactPersonName(blankToNull(values.get("contactPersonName")))
                        .approved(false)
                        .rejected(false)
                        .company(company)
                        .build();

                vendorRepo.save(vendor);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(error(rowNum, rowCode,
                        ex.getMessage() != null ? ex.getMessage() : "Import failed"));
            }
        }

        return VendorCsvImportResultDTO.builder()
                .created(created)
                .skipped(skipped)
                .failed(failed)
                .fieldMapping(fieldMapping)
                .aiMapped(aiMapped)
                .errors(errors)
                .build();
    }

    private Category resolveOrCreateCategory(String rawName, Company company, User actor) {
        String name = blankToNull(rawName);
        if (name == null) {
            return null;
        }
        String aliasKey = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String canonicalName = CATEGORY_ALIASES.getOrDefault(aliasKey, name.trim());

        Optional<Category> existing = categoryRepo
                .findByCompanyIdAndParentIdIsNullAndNameIgnoreCase(company.getId(), canonicalName);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Try original name if alias differed
        if (!canonicalName.equalsIgnoreCase(name.trim())) {
            existing = categoryRepo.findByCompanyIdAndParentIdIsNullAndNameIgnoreCase(
                    company.getId(), name.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String codeBase = canonicalName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (codeBase.length() > 8) {
            codeBase = codeBase.substring(0, 8);
        }
        if (codeBase.isBlank()) {
            codeBase = "CAT";
        }
        String code = "CAT-" + codeBase;
        int suffix = 1;
        while (categoryRepo.existsByCompanyIdAndParentIdAndCode(company.getId(), null, code)) {
            code = "CAT-" + codeBase + suffix;
            suffix++;
        }

        Category created = Category.builder()
                .code(code)
                .name(canonicalName)
                .status("active")
                .parent(null)
                .company(company)
                .createdByUser(actor)
                .updatedByUser(actor)
                .build();
        return categoryRepo.save(created);
    }

    private Map<String, String> parseClientMapping(String mappingJson, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    mappingJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> cleaned = new LinkedHashMap<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (String header : headers) {
                if (VendorCsvColumnMapperService.isArabicHeader(header)) {
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
                for (String field : VendorCsvColumnMapperService.CANONICAL_FIELDS) {
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
                if (!line.isBlank()) {
                    allRows.add(parseCsvLine(line));
                }
            }
            return new ParsedCsv(headers, allRows, allRows.stream().limit(3).toList());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read CSV: " + ex.getMessage(), ex);
        }
    }

    private static VendorCsvImportResultDTO.RowError error(int row, String code, String message) {
        return VendorCsvImportResultDTO.RowError.builder()
                .row(row).code(code).message(message).build();
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

    private static boolean normalizeActive(String status) {
        if (status == null || status.isBlank()) return true;
        String n = status.trim().toLowerCase(Locale.ROOT);
        return !(n.startsWith("inact") || n.equals("no") || n.equals("0") || n.equals("disabled") || n.equals("false"));
    }

    private static BigDecimal parseOptionalDecimal(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) return null;
        String numeric = cleaned.replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (numeric.isBlank() || numeric.equals("-") || numeric.equals(".")) return null;
        return new BigDecimal(numeric);
    }

    private record ParsedCsv(List<String> headers, List<List<String>> rows, List<List<String>> samples) {}
}
