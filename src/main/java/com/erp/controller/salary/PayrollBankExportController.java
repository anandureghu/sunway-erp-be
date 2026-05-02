package com.erp.controller.salary;

import com.erp.service.salary.PayrollBankFileExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/companies/{companyId}/payroll")
@RequiredArgsConstructor
public class PayrollBankExportController {

    private final PayrollBankFileExportService payrollBankFileExportService;

    @GetMapping(value = "/bank-payroll.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> downloadBankPayroll(
            @PathVariable("companyId") Long companyId,
            @RequestParam("yearMonth") String yearMonth) {

        String csv = payrollBankFileExportService.buildBankPayrollCsv(companyId, yearMonth);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payroll-" + yearMonth + ".csv\"")
                .body(body);
    }
}
