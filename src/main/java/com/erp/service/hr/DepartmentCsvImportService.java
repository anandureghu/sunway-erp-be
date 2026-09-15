package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.DepartmentCsvImportResultDTO;
import com.erp.dto.hr.DepartmentCsvImportResultDTO.RowError;
import com.erp.dto.hr.DepartmentCsvPreviewDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.hr.DepartmentCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DepartmentCsvImportService {

    private final DepartmentRepository departmentRepo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;
    private final DepartmentCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public DepartmentCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
        List<String> warnings = new ArrayList<>();
        if (!csv.headers().isEmpty() && !mr.mapping().containsValue("departmentCode") && !mr.mapping().containsValue("departmentName"))
            warnings.add("Could not detect department code or name columns — check headers");
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples())
            sampleMapped.add(extractValues(csv.headers(), row, mr.mapping()));
        return DepartmentCsvPreviewDTO.builder()
                .headers(csv.headers())
                .fieldMapping(mr.mapping())
                .aiMapped(mr.aiMapped())
                .dataRowCount(csv.rows().size())
                .sampleRows(sampleMapped)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public DepartmentCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);
        Map<String, String> fieldMapping;
        if (mappingJson != null && !mappingJson.isBlank()) {
            fieldMapping = parseClientMapping(mappingJson, csv.headers());
        } else {
            MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
            fieldMapping = mr.mapping();
        }

        Long companyId = auth.getCurrentCompanyId();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 0; i < csv.rows().size(); i++) {
            int rowNum = i + 2;
            try {
                Map<String, String> vals = extractValues(csv.headers(), csv.rows().get(i), fieldMapping);
                String code = blankToNull(vals.get("departmentCode"));
                String name = blankToNull(vals.get("departmentName"));
                if (name == null) { skipped++; continue; }
                if (code == null)
                    code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").substring(0, Math.min(10, name.replaceAll("[^A-Z0-9]", "").length()));

                Optional<Department> existingOpt = departmentRepo.findByDepartmentCodeIgnoreCaseAndCompany_Id(code, companyId);
                if (existingOpt.isPresent()) {
                    Department existing = existingOpt.get();
                    existing.setDepartmentName(name);
                    String desc = blankToNull(vals.get("description"));
                    if (desc != null) existing.setDescription(desc);
                    departmentRepo.save(existing);
                    updated++;
                    continue;
                }
                Department dept = Department.builder()
                        .departmentCode(code)
                        .departmentName(name)
                        .description(blankToNull(vals.get("description")))
                        .company(company)
                        .build();
                departmentRepo.save(dept);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(RowError.builder().row(rowNum).message(ex.getMessage()).build());
            }
        }
        return DepartmentCsvImportResultDTO.builder()
                .created(created).updated(updated).skipped(skipped).failed(failed).errors(errors).build();
    }

    private Map<String, String> parseClientMapping(String json, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> result = new LinkedHashMap<>();
            for (String h : headers) result.put(h, raw.getOrDefault(h, null));
            return result;
        } catch (Exception e) {
            return columnMapper.mapHeaders(headers, List.of()).mapping();
        }
    }

    private ParsedCsv parseFile(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(l -> !l.isBlank()).toList();
            if (lines.isEmpty()) return new ParsedCsv(List.of(), List.of(), List.of());
            List<String> headers = parseCsvLine(lines.get(0));
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) rows.add(parseCsvLine(lines.get(i)));
            List<List<String>> samples = rows.stream().limit(3).toList();
            return new ParsedCsv(headers, rows, samples);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read CSV: " + ex.getMessage(), ex);
        }
    }

    private static Map<String, String> extractValues(List<String> headers, List<String> cols, Map<String, String> mapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String canonical = mapping.get(headers.get(i));
            if (canonical == null || canonical.isBlank() || values.containsKey(canonical)) continue;
            values.put(canonical, i < cols.size() ? cols.get(i) : null);
        }
        return values;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQ = false;
                } else cur.append(c);
            } else if (c == '"') inQ = true;
            else if (c == ',') { result.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(c);
        }
        result.add(cur.toString().trim());
        return result;
    }

    private record ParsedCsv(List<String> headers, List<List<String>> rows, List<List<String>> samples) {}
}
