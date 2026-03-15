package com.erp.controller.salary;

import com.erp.service.salary.PayslipDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/payroll")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipDocumentService payslipDocumentService;

    @GetMapping("/{payrollCode}/payslip")
    public ResponseEntity<byte[]> downloadPayslip(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("payrollCode") String payrollCode
    ) {
        byte[] pdf = payslipDocumentService
                .generatePayslipPdf(employeeId, payrollCode);

        String filename = "payslip-" + payrollCode + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}