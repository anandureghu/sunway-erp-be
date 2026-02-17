package com.erp.domain;

import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
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

    @Transactional
    @Scheduled(cron = "0 0 0 1 1 *") // Every Jan 1st at midnight
    public void resetYearlyLeaves() {

        List<EmployeeLeaveBalance> balances = balanceRepo.findAll();

        for (EmployeeLeaveBalance balance : balances) {

            String role = balance.getEmployee().getRole();

            CompanyLeavePolicy policy =
                    policyRepo.findByCompanyAndRoleAndLeaveType(
                                    balance.getEmployee().getCompany(),
                                    role,
                                    balance.getLeaveType())
                            .orElse(null);

            if (policy != null && policy.isPaid()) {

                balance.setTotalLeaves(policy.getDefaultDays());
                balance.setRemainingLeaves(policy.getDefaultDays());

                balanceRepo.save(balance);
            }
        }
    }
}
