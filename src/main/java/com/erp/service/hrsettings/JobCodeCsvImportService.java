package com.erp.service.hrsettings;

import com.erp.domain.enums.EmploymentCategory;
import com.erp.domain.enums.EmploymentType;
import com.erp.domain.enums.JobCodeStatus;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.hr.Division;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.hrsettings.JobCodeCsvImportResultDTO;
import com.erp.dto.hrsettings.JobCodeCsvImportResultDTO.RowError;
import com.erp.dto.hrsettings.JobCodeCsvPreviewDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.hrsettings.JobCodeCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JobCodeCsvImportService {

    private final JobCodeRepository jobCodeRepo;
    private final CompanyRepository companyRepo;
    private final DepartmentRepository departmentRepo;
    private final DivisionRepository divisionRepo;
    private final AuthContext auth;
    private final JobCodeCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public JobCodeCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
        List<String> warnings = new ArrayList<>();
        if (!mr.mapping().containsValue("code") || !mr.mapping().containsValue("title"))
            warnings.add("Could not detect job code or title columns — check headers");
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples())
            sampleMapped.add(extractValues(csv.headers(), row, mr.mapping()));
        return JobCodeCsvPreviewDTO.builder()
                .headers(csv.headers()).fieldMapping(mr.mapping()).aiMapped(mr.aiMapped())
                .dataRowCount(csv.rows().size()).sampleRows(sampleMapped).warnings(warnings).build();
    }

    @Transactional
    public JobCodeCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);
        Map<String, String> fieldMapping;
        if (mappingJson != null && !mappingJson.isBlank()) {
            fieldMapping = parseClientMapping(mappingJson, csv.headers());
        } else {
            fieldMapping = columnMapper.mapHeaders(csv.headers(), csv.samples()).mapping();
        }

        Long companyId = auth.getCurrentCompanyId();
        Company company = companyRepo.findById(companyId).orElseThrow(() -> new RuntimeException("Company not found"));

        int created = 0, skipped = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 0; i < csv.rows().size(); i++) {
            int rowNum = i + 2;
            try {
                Map<String, String> vals = extractValues(csv.headers(), csv.rows().get(i), fieldMapping);
                String code = blankToNull(vals.get("code"));
                String title = blankToNull(vals.get("title"));
                if (code == null || title == null) { skipped++; continue; }
                code = code.toUpperCase(Locale.ROOT);

                if (jobCodeRepo.findByCompany_IdAndCode(companyId, code).isPresent()) { skipped++; continue; }

                Department dept = null;
                String deptName = blankToNull(vals.get("departmentName"));
                if (deptName != null)
                    dept = departmentRepo.findByDepartmentNameIgnoreCaseAndCompany_Id(deptName, companyId).orElse(null);

                Division div = null;
                String divName = blankToNull(vals.get("divisionName"));
                if (divName != null)
                    div = divisionRepo.findByNameIgnoreCaseAndCompany_Id(divName, companyId).orElse(null);

                String level = blankToDefault(vals.get("level"), "Mid");
                String grade = blankToDefault(vals.get("salaryGrade"), "G3");
                BigDecimal minSalary = parseDecimal(vals.get("minSalary"));
                BigDecimal maxSalary = parseDecimal(vals.get("maxSalary"));
                boolean active = parseBoolean(vals.get("active"), true);
                EmploymentCategory empCat = parseEnum(EmploymentCategory.class, vals.get("employmentCategory"));
                EmploymentType empType = parseEnum(EmploymentType.class, vals.get("employmentType"));
                String workLoc = blankToNull(vals.get("workLocation"));
                String workCity = blankToNull(vals.get("workCity"));
                String workCountry = blankToNull(vals.get("workCountry"));

                JobCode jc = JobCode.builder()
                        .code(code).title(title).level(level).salaryGrade(grade)
                        .minSalary(minSalary).maxSalary(maxSalary).active(active)
                        .status(JobCodeStatus.PENDING_APPROVAL)
                        .company(company).department(dept).division(div)
                        .employmentCategory(empCat).employmentType(empType)
                        .workLocation(workLoc).workCity(workCity).workCountry(workCountry)
                        .build();
                jobCodeRepo.save(jc);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(RowError.builder().row(rowNum).message(ex.getMessage()).build());
            }
        }
        return JobCodeCsvImportResultDTO.builder().created(created).skipped(skipped).failed(failed).errors(errors).build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value) {
        String v = blankToNull(value);
        if (v == null) return null;
        try { return Enum.valueOf(clazz, v.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_')); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static BigDecimal parseDecimal(String value) {
        String v = blankToNull(value);
        if (v == null) return null;
        try { return new BigDecimal(v.replace(",", "").replaceAll("[^0-9.\\-]", "")); }
        catch (Exception e) { return null; }
    }

    private static boolean parseBoolean(String value, boolean defaultVal) {
        String v = blankToNull(value);
        if (v == null) return defaultVal;
        String n = v.toLowerCase(Locale.ROOT);
        if (n.equals("yes") || n.equals("true") || n.equals("1") || n.equals("active")) return true;
        if (n.equals("no") || n.equals("false") || n.equals("0") || n.equals("inactive")) return false;
        return defaultVal;
    }

    private Map<String, String> parseClientMapping(String json, List<String> headers) {
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> result = new LinkedHashMap<>();
            for (String h : headers) result.put(h, raw.getOrDefault(h, null));
            return result;
        } catch (Exception e) { return columnMapper.mapHeaders(headers, List.of()).mapping(); }
    }

    private ParsedCsv parseFile(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(l -> !l.isBlank()).toList();
            if (lines.isEmpty()) return new ParsedCsv(List.of(), List.of(), List.of());
            List<String> headers = parseCsvLine(lines.get(0));
            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) rows.add(parseCsvLine(lines.get(i)));
            return new ParsedCsv(headers, rows, rows.stream().limit(3).toList());
        } catch (Exception ex) { throw new RuntimeException("Failed to read CSV: " + ex.getMessage(), ex); }
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

    private static String blankToNull(String v) { return (v == null || v.isBlank()) ? null : v.trim(); }
    private static String blankToDefault(String v, String def) { String r = blankToNull(v); return r != null ? r : def; }

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
