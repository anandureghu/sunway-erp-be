package com.erp.service.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionCsvImportResultDTO;
import com.erp.dto.finance.TransactionCsvImportResultDTO.RowError;
import com.erp.dto.finance.TransactionCsvPreviewDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.TransactionCsvColumnMapperService.MappingResult;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TransactionCsvImportService {

    private final TransactionService txService;
    private final ChartOfAccountsRepository coaRepo;
    private final AuthContext auth;
    private final TransactionCsvColumnMapperService columnMapper;
    private final ObjectMapper objectMapper;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public TransactionCsvPreviewDTO preview(MultipartFile file) {
        ParsedCsv csv = parseFile(file);
        MappingResult mr = columnMapper.mapHeaders(csv.headers(), csv.samples());
        List<String> warnings = new ArrayList<>();
        if (!mr.mapping().containsValue("amount"))
            warnings.add("Could not detect amount column — check headers");
        if (!mr.mapping().containsValue("debitAccountCode") && !mr.mapping().containsValue("creditAccountCode"))
            warnings.add("Could not detect debit/credit account columns — check headers");
        List<Map<String, String>> sampleMapped = new ArrayList<>();
        for (List<String> row : csv.samples())
            sampleMapped.add(extractValues(csv.headers(), row, mr.mapping()));
        return TransactionCsvPreviewDTO.builder()
                .headers(csv.headers()).fieldMapping(mr.mapping()).aiMapped(mr.aiMapped())
                .dataRowCount(csv.rows().size()).sampleRows(sampleMapped).warnings(warnings).build();
    }

    @Transactional
    public TransactionCsvImportResultDTO importCsv(MultipartFile file, String mappingJson) {
        ParsedCsv csv = parseFile(file);
        Map<String, String> fieldMapping = mappingJson != null && !mappingJson.isBlank()
                ? parseClientMapping(mappingJson, csv.headers())
                : columnMapper.mapHeaders(csv.headers(), csv.samples()).mapping();

        Long companyId = auth.getCurrentCompanyId();
        int created = 0, skipped = 0, failed = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 0; i < csv.rows().size(); i++) {
            int rowNum = i + 2;
            try {
                Map<String, String> vals = extractValues(csv.headers(), csv.rows().get(i), fieldMapping);

                BigDecimal amount = parseMoney(vals.get("amount"));
                if (amount == null) { skipped++; continue; }

                Long debitAccountId = resolveAccountId(companyId, vals.get("debitAccountCode"));
                Long creditAccountId = resolveAccountId(companyId, vals.get("creditAccountCode"));
                if (debitAccountId == null && creditAccountId == null) { skipped++; continue; }

                LocalDate txDate = parseDate(vals.get("transactionDate"));
                String txType = blankToNull(vals.get("transactionType"));
                String description = blankToNull(vals.get("transactionDescription"));
                String source = blankToNull(vals.get("source"));

                CreateTransactionDTO dto = CreateTransactionDTO.builder()
                        .companyId(companyId)
                        .transactionDate(txDate != null ? txDate : LocalDate.now())
                        .transactionType(txType != null ? txType : "JOURNAL")
                        .amount(amount)
                        .debitAccount(debitAccountId)
                        .creditAccount(creditAccountId)
                        .transactionDescription(description)
                        .source(source)
                        .build();

                txService.create(dto);
                created++;
            } catch (Exception ex) {
                failed++;
                errors.add(RowError.builder().row(rowNum).message(ex.getMessage()).build());
            }
        }
        return TransactionCsvImportResultDTO.builder()
                .created(created).skipped(skipped).failed(failed).errors(errors).build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Long resolveAccountId(Long companyId, String code) {
        String c = blankToNull(code);
        if (c == null) return null;
        return coaRepo.findByCompany_IdAndAccountCode(companyId, c)
                .map(ChartOfAccounts::getId).orElse(null);
    }

    private static LocalDate parseDate(String v) {
        String s = blankToNull(v);
        if (s == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s.trim(), fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

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
