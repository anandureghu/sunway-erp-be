package com.erp.controller.salary;

import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.dto.salary.PayrollHistoryDTO;
import com.erp.service.salary.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    /* ========= GENERATE PAYROLL ========= */
    @PostMapping("/generate")
    public ResponseEntity<Void> generatePayroll(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody PayrollGenerateRequestDTO dto) {

        payrollService.generatePayroll(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    /* ========= PAYROLL HISTORY ========= */
    @GetMapping("/history")
    public ResponseEntity<List<PayrollHistoryDTO>> history(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                payrollService.getPayrollHistory(employeeId)
        );
    }
}
