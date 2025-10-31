package com.hrmodule.service;

import com.hrmodule.domain.Employee;
import com.hrmodule.domain.Loan;
import com.hrmodule.dto.loan.LoanRequest;
import com.hrmodule.dto.loan.LoanResponse;
import com.hrmodule.repo.EmployeeRepository;
import com.hrmodule.repo.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepo;
    private final EmployeeRepository employeeRepo;

    public List<LoanResponse> listForEmployee(Long employeeId) {
        return loanRepo.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(LoanResponse::from).toList();
    }

    /**
     * Upsert for an employee: if req.id is null -> create; else update that loan row.
     */
    @Transactional
    public LoanResponse upsertForEmployee(Long employeeId, LoanRequest req) {
        Employee emp = employeeRepo.findById(employeeId).orElseThrow();

        Loan loan;
        if (req.getId() == null) {
            loan = new Loan();
            loan.setEmployee(emp);
        } else {
            loan = loanRepo.findById(req.getId()).orElseThrow();
            // Optional: ensure the same employee; if you want to allow moving loans, handle here.
            loan.setEmployee(emp);
        }

        applyInputs(loan, req);
        compute(loan); // if you have derived fields to compute; safe to keep no-ops

        return LoanResponse.from(loanRepo.save(loan));
    }

    // ----------------- helpers -----------------

    private void applyInputs(Loan l, LoanRequest r) {
        l.setLoanCode(safe(r.getLoanCode()));
        l.setLoanType(safe(r.getLoanType()));
        l.setLoanStatus(safe(r.getLoanStatus()));
        l.setStartDate(r.getStartDate());

        // loanPeriod comes from FE as String -> parse safely to Integer if your entity uses Integer
        Integer periodMonths = toIntegerOrNull(r.getLoanPeriod());
        l.setLoanPeriod(String.valueOf(periodMonths));

        l.setLoanAmount(nz(r.getLoanAmount()));
        l.setMonthlyDeductions(nz(r.getMonthlyDeductions()));
        l.setBalance(nz(r.getBalance()));
        l.setNotes(safe(r.getNotes()));

        // company property sidecar info
        l.setItemCode(safe(r.getItemCode()));
        l.setItemName(safe(r.getItemName()));
        l.setItemStatus(safe(r.getItemStatus()));
        l.setItemDescription(safe(r.getItemDescription()));
        l.setDateGiven(r.getDateGiven());
        l.setReturnDate(r.getReturnDate());
    }

    /**
     * Put any derived calculations here.
     * For example, if you want to auto-calc next balance after this month’s deduction
     * (purely illustrative; keep/adjust as your business rules require).
     */
    private void compute(Loan l) {
        // Example: ensure non-negative balance if someone sent negative numbers
        if (l.getBalance() != null && l.getBalance().signum() < 0) {
            l.setBalance(BigDecimal.ZERO);
        }
        // If you want to do auto recompute of monthly deduction when period is set, you can do it here:
        // if (l.getLoanAmount() != null && l.getLoanAmount().signum() > 0 && l.getLoanPeriod() != null && l.getLoanPeriod() > 0) {
        //     BigDecimal perMonth = l.getLoanAmount().divide(new BigDecimal(l.getLoanPeriod()), 2, RoundingMode.HALF_UP);
        //     l.setMonthlyDeductions(perMonth);
        // }
    }

    // ----------------- util -----------------

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Integer toIntegerOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException ex) {
            return null; // ignore bad input; or set to 0 if you prefer
        }
    }
}
