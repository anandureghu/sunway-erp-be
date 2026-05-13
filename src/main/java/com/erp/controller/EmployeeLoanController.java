package com.erp.controller;

import com.erp.domain.LoanType;
import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.service.EmployeeLoanService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees/{employeeId}/loans")
@RequiredArgsConstructor
public class EmployeeLoanController {

    private final EmployeeLoanService loanService;

    // ======================================================
    // GET LOAN TYPES (picklist)
    // VIEW_OWN — needed to populate the loan type dropdown
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/types")
    public ResponseEntity<List<LoanTypeDTO>> getLoanTypes() {
        List<LoanTypeDTO> types = Arrays.stream(LoanType.values())
                .map(lt -> new LoanTypeDTO(lt.name(), lt.getDisplayName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    // ======================================================
    // GET ALL LOANS FOR EMPLOYEE
    // VIEW_OWN — user views their own loans
    // VIEW_ALL — admin/HR views anyone's loans
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<LoanResponseDTO>> getLoans(
            @PathVariable("employeeId") Long employeeId) {
        return ResponseEntity.ok(loanService.getEmployeeLoans(employeeId));
    }

    // ======================================================
    // GET LOAN BY ID
    // VIEW_OWN — user views their own loan detail
    // VIEW_ALL — admin/HR views anyone's loan detail
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponseDTO> getLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId) {
        return ResponseEntity.ok(loanService.getLoanById(loanId));
    }

    // ======================================================
    // APPLY FOR LOAN
    // CREATE — applying for a new loan
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<LoanResponseDTO> applyLoan(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody LoanRequestDTO dto) {
        return ResponseEntity.ok(loanService.applyLoan(employeeId, dto));
    }

    // ======================================================
    // UPDATE LOAN
    // EDIT — updating an existing loan
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.EDIT})
    @PutMapping("/{loanId}")
    public ResponseEntity<LoanResponseDTO> updateLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @Valid @RequestBody LoanRequestDTO dto) {
        return ResponseEntity.ok(loanService.updateLoan(employeeId, loanId, dto));
    }

    // ======================================================
    // APPROVE / REJECT LOAN
    // APPROVE — only Finance Manager / HR Manager (or anyone the admin
    // explicitly grants LOANS.APPROVE) can decide a pending loan.
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.APPROVE})
    @PostMapping("/{loanId}/decision")
    public ResponseEntity<LoanResponseDTO> decideLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @Valid @RequestBody LoanDecisionDTO body) {
        return ResponseEntity.ok(loanService.decideLoan(loanId, body.isApprove()));
    }

    // ======================================================
    // MAKE LOAN PAYMENT
    // EDIT — making a payment modifies the loan record
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.EDIT})
    @PostMapping("/{loanId}/payment")
    public ResponseEntity<LoanResponseDTO> makePayment(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @Valid @RequestBody PaymentDTO payment) {
        return ResponseEntity.ok(loanService.makePayment(loanId, payment.getAmount()));
    }

    // ======================================================
    // DELETE LOAN
    // DELETE — removing a loan record
    // ======================================================
    @HrPermission(module = HrModule.LOANS, action = {HrAction.DELETE})
    @DeleteMapping("/{loanId}")
    public ResponseEntity<Void> deleteLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId) {
        loanService.deleteLoan(employeeId, loanId);
        return ResponseEntity.noContent().build();
    }

    // ======================================================
    // INNER DTOs
    // ======================================================
    @lombok.Data
    @lombok.AllArgsConstructor
    static class LoanTypeDTO {
        private String value;
        private String label;
    }

    @lombok.Data
    static class PaymentDTO {
        private Double amount;
    }

    @lombok.Data
    static class LoanDecisionDTO {
        private boolean approve;
    }
}