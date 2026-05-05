package com.erp.controller.salary;

import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.dto.salary.PayrollHistoryDTO;
import com.erp.dto.salary.PayrollPreviewDTO;
import com.erp.service.salary.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    /* ========= PREVIEW PAYROLL ========= */
    @PostMapping("/preview")
    public ResponseEntity<PayrollPreviewDTO> previewPayroll(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody PayrollGenerateRequestDTO dto) {

        return ResponseEntity.ok(
                payrollService.previewPayroll(employeeId, dto)
        );
    }

    /* ========= GENERATE PAYROLL ========= */
    @PostMapping("/generate")
    public ResponseEntity<List<PayrollHistoryDTO>> generatePayroll(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody PayrollGenerateRequestDTO dto) {

        payrollService.generatePayroll(employeeId, dto);

        return ResponseEntity.ok(
                payrollService.getPayrollHistory(employeeId)
        );
    }

    /* ========= PAYROLL HISTORY ========= */
    @GetMapping("/history")
    public ResponseEntity<List<PayrollHistoryDTO>> history(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                payrollService.getPayrollHistory(employeeId)
        );
    }

    /* ========= LATEST GENERATED PAYROLL IN A MONTH ========= */
    @GetMapping("/latest")
    public ResponseEntity<PayrollHistoryDTO> latestPayrollForMonth(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        return ResponseEntity.ok(
                payrollService.getLatestPayrollForMonth(employeeId, start, end)
        );
    }
}