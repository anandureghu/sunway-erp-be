package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Category;
import com.erp.dto.inventory.CategoryCsvImportResultDTO;
import com.erp.dto.inventory.CategoryCsvPreviewDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CategoryRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.inventory.CategoryCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bulk import categories / sub-categories from CSV.
 * Supports preview (map headers) then confirm import with optional client overrides.
 */
@Service
public class CategoryCsvImportService {

    private final CategoryRepository categoryRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final CategoryCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public CategoryCsvImportService(
            CategoryRepository categoryRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            CategoryCsvColumnMapperService columnMapper,
            ObjectMapper objectMapper
    ) {
        this.categoryRepo = categoryRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.columnMapper = columnMapper;
        this.objectMapper = objectMapper;
    }

    public CategoryCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mappingResult = columnMapper.mapHeaders(csv.headers(), csv.samples());
        Map<String, String> mapping = mappingResult.mapping();

        List<String> warnings = new ArrayList<>();
        if (!hasAny(mapping, "categoryCode", "categoryName")) {
            warnings.add("Could not map category code or name — check column headers");
        }
        for (String header : csv.headers()) {
            if (CategoryCsvColumnMapperService.isArabicHeader(header)
                    && mapping.get(header) != null) {
                warnings.add("Arabic column should be ignored: " + header);
            }
        }

        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples()) {
            sampleMapped.add(extractCanonicalValues(csv.headers(), row, mapping));
        }

        return CategoryCsvPreviewDTO.builder()
                .headers(csv.headers())
                .fieldMapping(mapping)
                .aiMapped(mappingResult.aiMapped())
                .dataRowCount(csv.rows().size())
                .sampleRows(sampleMapped)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public CategoryCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
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

        if (!hasAny(fieldMapping, "categoryCode", "categoryName")) {
            throw new IllegalArgumentException(
                    "Could not map required columns (need category code or name). "
                            + "Detected headers: " + String.join(", ", csv.headers()));
        }

        Long companyId = auth.getCurrentCompanyId();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        int created = 0;
        int skipped = 0;
        int failed = 0;
        int parentsCreated = 0;
        int subCategoriesCreated = 0;
        List<CategoryCsvImportResultDTO.RowError> errors = new ArrayList<>();

        String carryCategoryCode = null;
        String carryCategoryName = null;

        int rowNum = 1;
        for (List<String> cols : csv.rows()) {
            rowNum++;
            String rowCode = null;
            try {
                Map<String, String> values = extractCanonicalValues(csv.headers(), cols, fieldMapping);

                String categoryCode = blankToNull(values.get("categoryCode"));
                String categoryName = blankToNull(values.get("categoryName"));
                String subCode = blankToNull(values.get("subCategoryCode"));
                String subName = blankToNull(values.get("subCategoryName"));
                String gl = blankToNull(values.get("glAccountCode"));
                String status = CategoryService.normalizeStatus(values.get("status"));

                // Carry forward parent when Excel-style blank cells appear
                if (categoryCode != null) {
                    carryCategoryCode = categoryCode;
                } else {
                    categoryCode = carryCategoryCode;
                }
                if (categoryName != null) {
                    carryCategoryName = categoryName;
                } else {
                    categoryName = carryCategoryName;
                }

                if (categoryCode == null && categoryName == null) {
                    throw new IllegalArgumentException("Category code or name is required");
                }

                rowCode = subCode != null ? subCode : categoryCode;

                ParentResult parentResult = upsertParent(
                        company, user, companyId, categoryCode, categoryName, status,
                        // GL on parent only when row has no subcategory
                        (subCode == null && subName == null) ? gl : null
                );
                if (parentResult.created()) {
                    parentsCreated++;
                    created++;
                }

                if (subCode == null && subName == null) {
                    if (!parentResult.created()) {
                        skipped++;
                        errors.add(CategoryCsvImportResultDTO.RowError.builder()
                                .row(rowNum)
                                .code(categoryCode)
                                .message("Category already exists — skipped")
                                .build());
                    }
                    continue;
                }

                ChildResult childResult = upsertChild(
                        company, user, companyId, parentResult.category(),
                        subCode, subName, status, gl
                );
                if (childResult.created()) {
                    subCategoriesCreated++;
                    created++;
                } else {
                    skipped++;
                    errors.add(CategoryCsvImportResultDTO.RowError.builder()
                            .row(rowNum)
                            .code(subCode != null ? subCode : subName)
                            .message("Sub-category already exists — skipped")
                            .build());
                }
            } catch (Exception ex) {
                failed++;
                errors.add(CategoryCsvImportResultDTO.RowError.builder()
                        .row(rowNum)
                        .code(rowCode)
                        .message(ex.getMessage() != null ? ex.getMessage() : "Import failed")
                        .build());
            }
        }

        return CategoryCsvImportResultDTO.builder()
                .created(created)
                .skipped(skipped)
                .failed(failed)
                .parentsCreated(parentsCreated)
                .subCategoriesCreated(subCategoriesCreated)
                .fieldMapping(fieldMapping)
                .aiMapped(aiMapped)
                .errors(errors)
                .build();
    }

    private ParentResult upsertParent(
            Company company,
            User user,
            Long companyId,
            String code,
            String name,
            String status,
            String glAccountCode
    ) {
        Optional<Category> existing = Optional.empty();
        if (code != null) {
            existing = categoryRepo.findByCompanyIdAndParentIdIsNullAndCodeIgnoreCase(companyId, code);
        }
        if (existing.isEmpty() && name != null) {
            existing = categoryRepo.findByCompanyIdAndParentIdIsNullAndNameIgnoreCase(companyId, name);
        }
        if (existing.isPresent()) {
            Category cat = existing.get();
            boolean dirty = false;
            if (name != null && !name.equals(cat.getName())) {
                cat.setName(name);
                dirty = true;
            }
            if (glAccountCode != null && (cat.getGlAccountCode() == null || cat.getGlAccountCode().isBlank())) {
                cat.setGlAccountCode(glAccountCode);
                dirty = true;
            }
            if (dirty) {
                cat.setUpdatedByUser(user);
                categoryRepo.save(cat);
            }
            return new ParentResult(cat, false);
        }

        String resolvedCode = code != null ? code.trim() : synthesizeCode("CAT", name);
        String resolvedName = name != null ? name.trim() : resolvedCode;

        if (categoryRepo.existsByCompanyIdAndParentIdAndCode(companyId, null, resolvedCode)) {
            // Race / case variant — reload
            Category cat = categoryRepo.findByCompanyIdAndParentIdIsNullAndCodeIgnoreCase(companyId, resolvedCode)
                    .orElseThrow(() -> new IllegalArgumentException("Category code conflict: " + resolvedCode));
            return new ParentResult(cat, false);
        }

        Category created = Category.builder()
                .code(resolvedCode)
                .name(resolvedName)
                .status(status)
                .glAccountCode(glAccountCode)
                .parent(null)
                .company(company)
                .createdByUser(user)
                .updatedByUser(user)
                .build();
        return new ParentResult(categoryRepo.save(created), true);
    }

    private ChildResult upsertChild(
            Company company,
            User user,
            Long companyId,
            Category parent,
            String code,
            String name,
            String status,
            String glAccountCode
    ) {
        Optional<Category> existing = Optional.empty();
        if (code != null) {
            existing = categoryRepo.findByCompanyIdAndParentIdAndCodeIgnoreCase(
                    companyId, parent.getId(), code);
        }
        if (existing.isEmpty() && name != null) {
            existing = categoryRepo.findByCompanyIdAndParentIdAndNameIgnoreCase(
                    companyId, parent.getId(), name);
        }
        if (existing.isPresent()) {
            Category cat = existing.get();
            boolean dirty = false;
            if (name != null && !name.equals(cat.getName())) {
                cat.setName(name);
                dirty = true;
            }
            if (glAccountCode != null && (cat.getGlAccountCode() == null || cat.getGlAccountCode().isBlank())) {
                cat.setGlAccountCode(glAccountCode);
                dirty = true;
            }
            if (dirty) {
                cat.setUpdatedByUser(user);
                categoryRepo.save(cat);
            }
            return new ChildResult(cat, false);
        }

        String resolvedCode = code != null ? code.trim() : synthesizeCode("SC", name);
        String resolvedName = name != null ? name.trim() : resolvedCode;

        if (categoryRepo.existsByCompanyIdAndParentIdAndCode(companyId, parent.getId(), resolvedCode)) {
            Category cat = categoryRepo.findByCompanyIdAndParentIdAndCodeIgnoreCase(
                            companyId, parent.getId(), resolvedCode)
                    .orElseThrow(() -> new IllegalArgumentException("Sub-category code conflict: " + resolvedCode));
            return new ChildResult(cat, false);
        }

        Category created = Category.builder()
                .code(resolvedCode)
                .name(resolvedName)
                .status(status)
                .glAccountCode(glAccountCode)
                .parent(parent)
                .company(company)
                .createdByUser(user)
                .updatedByUser(user)
                .build();
        return new ChildResult(categoryRepo.save(created), true);
    }

    private Map<String, String> parseClientMapping(String mappingJson, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    mappingJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> cleaned = new LinkedHashMap<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            for (String header : headers) {
                if (CategoryCsvColumnMapperService.isArabicHeader(header)) {
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
                for (String field : CategoryCsvColumnMapperService.CANONICAL_FIELDS) {
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
            List<List<String>> samples = allRows.stream().limit(3).toList();
            return new ParsedCsv(headers, allRows, samples);
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

    private static String synthesizeCode(String prefix, String name) {
        String base = name == null ? prefix : name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.length() > 12) {
            base = base.substring(0, 12);
        }
        if (base.isBlank()) {
            base = prefix;
        }
        return prefix + "-" + base + "-" + (System.nanoTime() % 10000);
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

    private record ParsedCsv(List<String> headers, List<List<String>> rows, List<List<String>> samples) {}
    private record ParentResult(Category category, boolean created) {}
    private record ChildResult(Category category, boolean created) {}
}
