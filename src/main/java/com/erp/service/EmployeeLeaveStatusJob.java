package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.LeaveStatus;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Keeps each employee's status in sync with their approved leaves: an employee
 * with an approved leave covering today is ON_LEAVE; once that leave ends they
 * return to ACTIVE. Runs daily so future leaves flip to ON_LEAVE the day they
 * start and revert when they finish.
 *
 * Only ACTIVE &lt;-&gt; ON_LEAVE are ever changed; INACTIVE / resigned / terminated
 * statuses are left untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeLeaveStatusJob {

    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveRepository leaveRepo;

    /** Reconcile once on startup so existing approved leaves take effect immediately. */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        reconcileLeaveStatuses();
    }

    @Transactional
    @Scheduled(cron = "0 5 0 * * *") // every day at 00:05
    public void reconcileLeaveStatuses() {
        LocalDate today = LocalDate.now();

        int changed = 0;
        for (Employee emp : employeeRepo.findAll()) {
            EmployeeStatus status = emp.getStatus();
            // Only ACTIVE <-> ON_LEAVE are reconciled; leave every other state alone.
            if (status != EmployeeStatus.ACTIVE && status != EmployeeStatus.ON_LEAVE) {
                continue;
            }
            boolean isOnLeave = leaveRepo.existsLeavesForPayrollPeriod(
                    emp.getId(), LeaveStatus.APPROVED, today, today);

            if (status == EmployeeStatus.ACTIVE && isOnLeave) {
                emp.setStatus(EmployeeStatus.ON_LEAVE);
                employeeRepo.save(emp);
                changed++;
            } else if (status == EmployeeStatus.ON_LEAVE && !isOnLeave) {
                emp.setStatus(EmployeeStatus.ACTIVE);
                employeeRepo.save(emp);
                changed++;
            }
        }

        if (changed > 0) {
            log.info("Leave-status reconciliation updated {} employee(s)", changed);
        }
    }
}
