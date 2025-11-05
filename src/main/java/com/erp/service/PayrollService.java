package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Payroll;
import com.erp.domain.Loan;
import com.erp.dto.payroll.PayrollRequest;
import com.erp.dto.payroll.PayrollResponse;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.PayrollRepository;
import com.erp.repo.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRepository payrollRepo;
    private final EmployeeRepository employeeRepo;

    // Only needed if you’re deducting loans in compute()
    private final LoanRepository loanRepo;

    @Transactional
    public PayrollResponse create(PayrollRequest req) {
        Employee emp = employeeRepo.findById(req.getEmployeeId()).orElseThrow();
        Payroll p = new Payroll();
        p.setEmployee(emp);
        applyInputs(p, req);     // <-- this method is defined below
        compute(p);
        return PayrollResponse.from(payrollRepo.save(p));
    }

    @Transactional
    public PayrollResponse update(Long payrollId, PayrollRequest req) {
        Payroll p = payrollRepo.findById(payrollId).orElseThrow();
        // If you want to allow moving a record to another employee, handle that here
        applyInputs(p, req);     // <-- this method is defined below
        compute(p);
        return PayrollResponse.from(payrollRepo.save(p));
    }

    public PayrollResponse get(Long id) {
        return PayrollResponse.from(payrollRepo.findById(id).orElseThrow());
    }

    public List<PayrollResponse> listForEmployee(Long employeeId) {
        return payrollRepo.findByEmployeeIdOrderByPayPeriodDesc(employeeId)
                .stream()
                .map(PayrollResponse::from)
                .toList();
    }

    // ------------ helpers ------------

    /** Copies request fields into the entity. Keep names in sync with your DTO. */
    private void applyInputs(Payroll p, PayrollRequest r) {
        p.setPayrollCode(r.getPayrollCode());
        p.setPayPeriod(r.getPayPeriod());
        p.setPayPeriodEnd(r.getPayPeriodEnd());
        p.setWorkingPeriod(r.getWorkingPeriod());
        p.setPayDays(r.getPayDays());

        p.setMonthlyBasic(nz(r.getBasic()));
        p.setTransportation(safe(r.getTransportation()));
        p.setConveyanceAllowance(nz(r.getConveyanceAllowance()));
        p.setTravel(nz(r.getTravel()));
        p.setOtherCompensationAllowable(nz(r.getOtherCompensationAllowable()));

        p.setCompensationStatus(safe(r.getCompensationStatus()));
        p.setEffectiveFrom(r.getEffectiveFrom());
        p.setEffectiveTo(r.getEffectiveTo());

        p.setBankName(safe(r.getBankName()));
        p.setAccountNo(safe(r.getAccountNo()));
        p.setAccountType(safe(r.getAccountType()));
        p.setBankBranch(safe(r.getBankBranch()));
        p.setBankRemarks(safe(r.getBankRemarks()));
        p.setLocation(safe(r.getLocation()));
        p.setStreet(safe(r.getStreet()));
        p.setCity(safe(r.getCity()));
        p.setState(safe(r.getState()));
        p.setCountry(safe(r.getCountry()));
        p.setIban(safe(r.getIban()));
    }

    /** Recomputes derived fields (prorated basic, total allowances, net payable). */
    private void compute(Payroll p) {
        int working = Math.max(0, p.getWorkingPeriod() == null ? 0 : p.getWorkingPeriod());
        int days = Math.max(0, p.getPayDays() == null ? 0 : p.getPayDays());
        if (working > 0 && days > working) days = working;

        BigDecimal monthlyBasic = nz(p.getMonthlyBasic());
        BigDecimal dailyBasic = working > 0
                ? monthlyBasic.divide(BigDecimal.valueOf(working), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal proratedBasic = dailyBasic.multiply(BigDecimal.valueOf(days));

        BigDecimal totalAllowances = nz(p.getConveyanceAllowance())
                .add(nz(p.getTravel()))
                .add(nz(p.getOtherCompensationAllowable()));

        // Optional: include current-month loan deductions automatically
        BigDecimal loanDeduction = BigDecimal.ZERO;
        if (p.getEmployee() != null && p.getPayPeriodEnd() != null && loanRepo != null) {
            LocalDate periodEnd = p.getPayPeriodEnd();
            List<Loan> activeLoans = loanRepo.findActiveLoansForMonth(p.getEmployee().getId(), periodEnd);
            loanDeduction = activeLoans.stream()
                    .map(Loan::getMonthlyDeductions)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Add other deductions (PF/Tax/etc) here as you implement them
        BigDecimal pf = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal otherDed = BigDecimal.ZERO;

        BigDecimal net = proratedBasic.add(totalAllowances)
                .subtract(pf).subtract(tax).subtract(otherDed).subtract(loanDeduction);

        p.setBasicProrated(proratedBasic.setScale(2, RoundingMode.HALF_UP));
        p.setTotalAllowances(totalAllowances.setScale(2, RoundingMode.HALF_UP));
        p.setNetPayable(net.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
