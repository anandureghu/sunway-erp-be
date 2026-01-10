package com.erp.controller;

import com.erp.domain.LoanType;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.service.EmployeeLoanService;
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

    /* ========= GET LOAN TYPES (for picklist) ========= */
    @GetMapping("/types")
    public ResponseEntity<List<LoanTypeDTO>> getLoanTypes() {
        List<LoanTypeDTO> types = Arrays.stream(LoanType.values())
                .map(lt -> new LoanTypeDTO(lt.name(), lt.getDisplayName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    /* ========= APPLY FOR LOAN ========= */
    @PostMapping
    public ResponseEntity<LoanResponseDTO> applyLoan(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody LoanRequestDTO dto) {
        return ResponseEntity.ok(loanService.applyLoan(employeeId, dto));
    }

    /* ========= UPDATE LOAN ========= */
    @PutMapping("/{loanId}")
    public ResponseEntity<LoanResponseDTO> updateLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @RequestBody LoanRequestDTO dto) {
        return ResponseEntity.ok(loanService.updateLoan(employeeId, loanId, dto));
    }

    /* ========= GET EMPLOYEE LOANS ========= */
    @GetMapping
    public ResponseEntity<List<LoanResponseDTO>> getLoans(
            @PathVariable("employeeId") Long employeeId) {
        return ResponseEntity.ok(loanService.getEmployeeLoans(employeeId));
    }

    /* ========= GET LOAN BY ID ========= */
    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponseDTO> getLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId) {
        return ResponseEntity.ok(loanService.getLoanById(loanId));
    }

    /* ========= MAKE PAYMENT ========= */
    @PostMapping("/{loanId}/payment")
    public ResponseEntity<LoanResponseDTO> makePayment(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId,
            @RequestBody PaymentDTO payment) {
        return ResponseEntity.ok(loanService.makePayment(loanId, payment.getAmount()));
    }

    /* ========= DELETE LOAN ========= */
    @DeleteMapping("/{loanId}")
    public ResponseEntity<Void> deleteLoan(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("loanId") Long loanId) {
        loanService.deleteLoan(employeeId, loanId);
        return ResponseEntity.noContent().build();
    }

    // DTO for loan type picklist
    @lombok.Data
    @lombok.AllArgsConstructor
    static class LoanTypeDTO {
        private String value;
        private String label;
    }

    // DTO for payment
    @lombok.Data
    static class PaymentDTO {
        private Double amount;
    }
}
