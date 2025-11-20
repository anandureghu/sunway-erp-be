package com.erp.web;

import com.erp.dto.loan.LoanRequest;
import com.erp.dto.loan.LoanResponse;
import com.erp.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin // if your FE runs on a different origin
public class LoanController {

    private final LoanService loanService;

    @GetMapping("/employees/{employeeId}/loans")
    public List<LoanResponse> listLoans(@PathVariable("employeeId") Long employeeId) {
        return loanService.listForEmployee(employeeId);
    }

    @PostMapping("/employees/{employeeId}/loans/upsert")
    public LoanResponse upsert(@PathVariable("employeeId") Long employeeId,
                               @RequestBody LoanRequest req) {
        return loanService.upsertForEmployee(employeeId, req);
    }

    // Realtime dropdown meta (optional; FE has fallbacks)
    @GetMapping("/loans/meta/types")
    public List<String> loanTypes() {
        return List.of("Personal", "Home", "Car", "Education", "Emergency");
    }

    @GetMapping("/loans/meta/statuses")
    public List<String> loanStatuses() {
        return List.of("Active", "Closed", "Pending", "Defaulted", "Paused");
    }
}
