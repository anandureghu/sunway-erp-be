package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.service.EmployeeLoanService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanApprovalController {

    private final EmployeeLoanService loanService;

    // Company-wide list of pending loans for the caller's tenant.
    // Gated by LOANS.APPROVE so only HR Manager / Finance Manager (or whoever
    // the admin has granted approval rights to) can see this queue.
    @RequiresPermission(module = AppModule.LOANS, action = {AppAction.APPROVE})
    @GetMapping("/pending-approvals")
    public ResponseEntity<List<LoanResponseDTO>> pendingApprovals() {
        return ResponseEntity.ok(loanService.getPendingLoanApprovalsForCurrentCompany());
    }

    // Company-wide history of decided loans (active / closed / rejected) for the
    // HR Reports "Loan Approvals" view. Visible to loan viewers and approvers.
    @RequiresPermission(module = AppModule.LOANS, action = {AppAction.VIEW_ALL, AppAction.APPROVE})
    @GetMapping("/approvals-history")
    public ResponseEntity<List<LoanResponseDTO>> approvalsHistory(
            @RequestParam(name = "archived", defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(loanService.getCompanyLoanApprovals(archived));
    }

    // Archive / unarchive a decided loan so it drops from (or returns to) the
    // active Loan Approvals list. Gated by LOANS.APPROVE.
    @RequiresPermission(module = AppModule.LOANS, action = {AppAction.APPROVE})
    @PostMapping("/{loanId}/archive")
    public ResponseEntity<LoanResponseDTO> archiveLoan(
            @PathVariable("loanId") Long loanId,
            @RequestParam(name = "archived", defaultValue = "true") boolean archived) {
        return ResponseEntity.ok(loanService.setLoanArchived(loanId, archived));
    }

    // Permanently delete an archived (completed) loan record.
    @RequiresPermission(module = AppModule.LOANS, action = {AppAction.DELETE})
    @DeleteMapping("/{loanId}")
    public ResponseEntity<Void> deleteLoanRecord(@PathVariable("loanId") Long loanId) {
        loanService.deleteLoanRecord(loanId);
        return ResponseEntity.noContent().build();
    }
}
