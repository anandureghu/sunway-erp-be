package com.erp.service;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Department;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeCsvImportResultDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.dto.hr.EmployeeCsvImportResultDTO.RowError;
import com.erp.dto.hr.EmployeeCsvPreviewDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.EmployeeCsvColumnMapperService.MappingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeCsvImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private final EmployeeService employeeService;
    private final EmployeeContactInfoService contactInfoService;
    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final CompanyRoleRepository companyRoleRepo;
    private final AuthContext auth;
    private final EmployeeCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public EmployeeCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
        List<String> warnings = new ArrayList<>();
        if (!mr.mapping().containsValue("firstName") && !mr.mapping().containsValue("lastName"))
            warnings.add("Could not detect first name or last name columns — check headers");
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples())
            sampleMapped.add(extractValues(csv.headers(), row, mr.mapping()));
        return EmployeeCsvPreviewDTO.builder()
                .headers(csv.headers()).fieldMapping(mr.mapping()).aiMapped(mr.aiMapped())
                .dataRowCount(csv.rows().size()).sampleRows(sampleMapped).warnings(warnings).build();
    }

    public EmployeeCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);
        Map<String, String> fieldMapping;
        if (mappingJson != null && !mappingJson.isBlank()) {
            fieldMapping = parseClientMapping(mappingJson, csv.headers());
        } else {
            fieldMapping = columnMapper.mapHeaders(csv.headers(), csv.samples()).mapping();
        }

        Long companyId = auth.getCurrentCompanyId();
        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 0; i < csv.rows().size(); i++) {
            int rowNum = i + 2;
            String empNo = null;
            try {
                Map<String, String> vals = extractValues(csv.headers(), csv.rows().get(i), fieldMapping);
                String firstName = blankToNull(vals.get("firstName"));
                String lastName = blankToNull(vals.get("lastName"));
                if (firstName == null && lastName == null) { skipped++; continue; }
                if (firstName == null) firstName = lastName;
                if (lastName == null) lastName = firstName;

                empNo = blankToNull(vals.get("employeeNo"));

                Long departmentId = null;
                String deptName = blankToNull(vals.get("departmentName"));
                if (deptName != null) {
                    Optional<Department> dept = departmentRepo.findByDepartmentNameIgnoreCaseAndCompany_Id(deptName, companyId);
                    departmentId = dept.map(Department::getId).orElse(null);
                }

                Long companyRoleId = null;
                String roleName = blankToNull(vals.get("companyRole"));
                if (roleName != null) {
                    companyRoleId = companyRoleRepo.findByCompanyIdAndNameIgnoreCase(companyId, roleName)
                            .map(r -> r.getId()).orElse(null);
                }

                String phone = blankToNull(vals.get("phoneNo"));
                String altPhone = blankToNull(vals.get("altPhone"));
                String email = blankToNull(vals.get("email"));

                // Upsert: if employeeNo is provided and the employee already exists, update
                if (empNo != null) {
                    Optional<com.erp.domain.Employee> existingOpt = employeeRepo.findByCompany_IdAndEmployeeNo(companyId, empNo);
                    if (existingOpt.isPresent()) {
                        com.erp.domain.Employee existing = existingOpt.get();
                        UpdateEmployeeDTO updateDto = UpdateEmployeeDTO.builder()
                                .firstName(firstName)
                                .middleName(blankToNull(vals.get("middleName")))
                                .lastName(lastName)
                                .gender(blankToNull(vals.get("gender")))
                                .prefix(blankToNull(vals.get("prefix")))
                                .maritalStatus(blankToNull(vals.get("maritalStatus")))
                                .dateOfBirth(parseDate(vals.get("dateOfBirth")))
                                .joinDate(parseDate(vals.get("joinDate")))
                                .status(parseStatus(vals.get("status")))
                                .birthplace(blankToNull(vals.get("birthplace")))
                                .hometown(blankToNull(vals.get("hometown")))
                                .nationality(blankToNull(vals.get("nationality")))
                                .religion(blankToNull(vals.get("religion")))
                                .identification(blankToNull(vals.get("identification")))
                                .departmentId(departmentId)
                                .companyRoleId(companyRoleId)
                                .build();
                        EmployeeResponseDTO updatedEmp = employeeService.updateEmployee(existing.getId(), updateDto);
                        if (phone != null || altPhone != null || email != null) {
                            try {
                                contactInfoService.saveOrUpdateContactInfo(
                                        updatedEmp.getId(),
                                        EmployeeContactInfoRequestDTO.builder()
                                                .phone(phone).altPhone(altPhone).email(email).build()
                                );
                            } catch (Exception ignored) {}
                        }
                        updated++;
                        continue;
                    }
                }

                CreateEmployeeDTO dto = CreateEmployeeDTO.builder()
                        .firstName(firstName)
                        .middleName(blankToNull(vals.get("middleName")))
                        .lastName(lastName)
                        .gender(blankToNull(vals.get("gender")))
                        .prefix(blankToNull(vals.get("prefix")))
                        .maritalStatus(blankToNull(vals.get("maritalStatus")))
                        .dateOfBirth(parseDate(vals.get("dateOfBirth")))
                        .joinDate(parseDate(vals.get("joinDate")))
                        .status(parseStatus(vals.get("status")))
                        .birthplace(blankToNull(vals.get("birthplace")))
                        .hometown(blankToNull(vals.get("hometown")))
                        .nationality(blankToNull(vals.get("nationality")))
                        .religion(blankToNull(vals.get("religion")))
                        .identification(blankToNull(vals.get("identification")))
                        .companyId(companyId)
                        .departmentId(departmentId)
                        .companyRoleId(companyRoleId)
                        .build();

                EmployeeResponseDTO createdEmp = employeeService.createEmployee(dto);

                if (phone != null || altPhone != null || email != null) {
                    try {
                        contactInfoService.saveOrUpdateContactInfo(
                                createdEmp.getId(),
                                EmployeeContactInfoRequestDTO.builder()
                                        .phone(phone).altPhone(altPhone).email(email).build()
                        );
                    } catch (Exception ignored) {}
                }
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(RowError.builder().row(rowNum).employeeNo(empNo).message(ex.getMessage()).build());
            }
        }
        return EmployeeCsvImportResultDTO.builder().created(created).updated(updated).skipped(skipped).failed(failed).errors(errors).build();
    }

    private static LocalDate parseDate(String value) {
        String v = blankToNull(value);
        if (v == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(v, fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static EmployeeStatus parseStatus(String value) {
        String v = blankToNull(value);
        if (v == null) return EmployeeStatus.ACTIVE;
        try { return EmployeeStatus.valueOf(v.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_')); }
        catch (IllegalArgumentException e) { return EmployeeStatus.ACTIVE; }
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
