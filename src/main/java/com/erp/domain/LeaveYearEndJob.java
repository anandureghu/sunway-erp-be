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
    @Scheduled(cron = "0 0 0 1 1 *")
    public void resetYearlyLeaves() {

        List<EmployeeLeaveBalance> balances = balanceRepo.findAll();

        for (EmployeeLeaveBalance b : balances) {

            CompanyLeavePolicy policy =
                    policyRepo.findByCompanyAndLeaveType(
                            b.getEmployee().getCompany(),
                            b.getLeaveType()).orElse(null);

            if (policy != null && policy.isPaid()) {
                b.setTotalLeaves(policy.getDefaultDays());
                b.setRemainingLeaves(policy.getDefaultDays());
                balanceRepo.save(b);
            }
        }
    }
}