package com.erp.service.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.ChartOfAccountsCsvImportResultDTO;
import com.erp.dto.finance.ChartOfAccountsCsvImportResultDTO.RowError;
import com.erp.dto.finance.ChartOfAccountsCsvPreviewDTO;
import com.erp.dto.finance.CreateAccountDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.ChartOfAccountsCsvColumnMapperService.MappingResult;
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
public class ChartOfAccountsCsvImportService {

    private final ChartOfAccountsService coaService;
    private final ChartOfAccountsRepository coaRepo;
    private final DepartmentRepository departmentRepo;
    private final AuthContext auth;
    private final ChartOfAccountsCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    public ChartOfAccountsCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
        List<String> warnings = new ArrayList<>();
        if (!mr.mapping().containsValue("accountCode") && !mr.mapping().containsValue("accountName"))
            warnings.add("Could not detect account code or name column — check headers");
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples())
            sampleMapped.add(extractValues(csv.headers(), row, mr.mapping()));
        return ChartOfAccountsCsvPreviewDTO.builder()
                .headers(csv.headers()).fieldMapping(mr.mapping()).aiMapped(mr.aiMapped())
                .dataRowCount(csv.rows().size()).sampleRows(sampleMapped).warnings(warnings).build();
    }

    @Transactional
    public ChartOfAccountsCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);
        Map<String, String> fieldMapping = mappingJson != null && !mappingJson.isBlank()
                ? parseClientMapping(mappingJson, csv.headers())
                : columnMapper.mapHeaders(csv.headers(), csv.samples()).mapping();

        Long companyId = auth.getCurrentCompanyId();
        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 0; i < csv.rows().size(); i++) {
            int rowNum = i + 2;
            String accountCode = null;
            try {
                Map<String, String> vals = extractValues(csv.headers(), csv.rows().get(i), fieldMapping);
                accountCode = blankToNull(vals.get("accountCode"));
                String accountName = blankToNull(vals.get("accountName"));
                if (accountName == null) { skipped++; continue; }

                // Upsert by accountCode within company
                if (accountCode != null) {
                    Optional<ChartOfAccounts> existing = coaRepo.findByCompany_IdAndAccountCode(companyId, accountCode);
                    if (existing.isPresent()) {
                        ChartOfAccounts acc = existing.get();
                        acc.setAccountName(accountName);
                        String desc = blankToNull(vals.get("description"));
                        if (desc != null) acc.setDescription(desc);
                        coaRepo.save(acc);
                        updated++;
                        continue;
                    }
                }

                // Resolve parent
                Long parentId = null;
                String parentCode = blankToNull(vals.get("parentAccountCode"));
                if (parentCode != null) {
                    parentId = coaRepo.findByCompany_IdAndAccountCode(companyId, parentCode)
                            .map(ChartOfAccounts::getId).orElse(null);
                }

                // Resolve department
                Long departmentId = null;
                String deptName = blankToNull(vals.get("departmentName"));
                if (deptName != null) {
                    departmentId = departmentRepo
                            .findByDepartmentNameIgnoreCaseAndCompany_Id(deptName, companyId)
                            .map(Department::getId).orElse(null);
                }

                // Opening balance
                BigDecimal openingBalance = parseMoney(vals.get("openingBalance"));

                CreateAccountDTO dto = new CreateAccountDTO();
                dto.setAccountCode(accountCode);
                dto.setAccountName(accountName);
                dto.setDescription(blankToNull(vals.get("description")));
                dto.setType(blankToNull(vals.get("type")));
                dto.setAccountNo(blankToNull(vals.get("accountNo")));
                dto.setParentId(parentId);
                dto.setDepartmentId(departmentId);
                dto.setProjectCode(blankToNull(vals.get("projectCode")));
                dto.setOpeningBalance(openingBalance);

                coaService.createAccount(dto);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(RowError.builder().row(rowNum).code(accountCode).message(ex.getMessage()).build());
            }
        }
        return ChartOfAccountsCsvImportResultDTO.builder()
                .created(created).updated(updated).skipped(skipped).failed(failed).errors(errors).build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static BigDecimal parseMoney(String v) {
        String s = blankToNull(v);
        if (s == null) return null;
        s = s.replaceAll("[,\"'\\s]", "").replaceAll("[^0-9.\\-]", "");
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
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
