package com.erp.domain;

import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.service.LeavePolicyKeyResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveYearEndJob {

    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final LeavePolicyKeyResolver keyResolver;

    @Transactional
    @Scheduled(cron = "0 0 0 1 1 *") // Every Jan 1st at midnight
    public void resetYearlyLeaves() {

        List<EmployeeLeaveBalance> balances = balanceRepo.findAll();

        for (EmployeeLeaveBalance balance : balances) {

            Employee employee = balance.getEmployee();

            // Leave policies are keyed by job code (with role fallbacks). Try the
            // employee's resolved keys in order until a matching policy is found.
            CompanyLeavePolicy policy = null;
            for (String matchKey : keyResolver.keysFor(employee)) {
                policy = policyRepo.findByCompanyAndJobCodeAndLeaveType(
                                employee.getCompany(),
                                matchKey,
                                balance.getLeaveType())
                        .orElse(null);
                if (policy != null) {
                    break;
                }
            }

            if (policy != null && policy.isPaid()) {

                // Annual leave that runs on company-wide accrual is recomputed
                // live from join-date by LeaveService — don't blow it away
                // every January.
                boolean accruedAnnual =
                        balance.getEmployee().getCompany() != null
                                && balance.getEmployee().getCompany().isAnnualLeaveAccrualEnabled()
                                && balance.getLeaveType() != null
                                && balance.getLeaveType().toUpperCase().contains("ANNUAL");

                if (accruedAnnual) {
                    continue;
                }

                balance.setTotalLeaves(policy.getDefaultDays());
                balance.setRemainingLeaves(policy.getDefaultDays());

                balanceRepo.save(balance);
            }
        }
    }
}
