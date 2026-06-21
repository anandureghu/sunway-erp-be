package com.erp.controller.salary;

import com.erp.dto.salary.PayrollAccountStatusDTO;
import com.erp.dto.salary.PayrollBatchResponseDTO;
import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.service.salary.PayrollBankFileExportService;
import com.erp.service.salary.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/companies/{companyId}/payroll")
@RequiredArgsConstructor
public class PayrollBankExportController {

    private final PayrollBankFileExportService payrollBankFileExportService;
    private final PayrollService payrollService;

    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).PAYROLL, T(com.erp.domain.security.AppAction).VIEW_ALL)")
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

    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).PAYROLL, T(com.erp.domain.security.AppAction).VIEW_ALL)")
    @GetMapping("/account-status")
    public ResponseEntity<PayrollAccountStatusDTO> getPayrollAccountStatus(
            @PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(payrollService.getPayrollAccountStatus(companyId));
    }

    /**
     * Creates payroll for every active employee in one transaction. Fails with 400 and a {@code details} list
     * if any employee is missing required data or already has payroll for the pay-date month.
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).PAYROLL, T(com.erp.domain.security.AppAction).CREATE)")
    @PostMapping("/generate-batch")
    public ResponseEntity<PayrollBatchResponseDTO> generatePayrollBatch(
            @PathVariable("companyId") Long companyId,
            @RequestBody PayrollGenerateRequestDTO body) {
        return ResponseEntity.ok(payrollService.generatePayrollBatch(companyId, body));
    }
}
