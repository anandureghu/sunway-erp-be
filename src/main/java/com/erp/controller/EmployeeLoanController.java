package com.erp.controller;

import com.erp.dto.loan.LoanRequestDTO;

import com.erp.dto.loan.LoanResponseDTO;
import com.erp.service.EmployeeLoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/loans")
@RequiredArgsConstructor
public class EmployeeLoanController {

    private final EmployeeLoanService service;

    /* ================= GET ALL LOANS ================= */

    @GetMapping
    public ResponseEntity<List<LoanResponseDTO>> getLoans(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                service.getLoansByEmployee(employeeId)
        );
    }

    /* ================= CREATE LOAN ================= */

    @PostMapping
    public ResponseEntity<Void> createLoan(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody LoanRequestDTO dto) {

        service.createLoan(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    /* ================= UPDATE LOAN ================= */

    @PutMapping("/{loanId}")
    public ResponseEntity<Void> updateLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @RequestBody LoanRequestDTO dto) {

        service.updateLoan(employeeId, loanId, dto);
        return ResponseEntity.ok().build();
    }
}
