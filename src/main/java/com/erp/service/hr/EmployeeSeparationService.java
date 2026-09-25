package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.repo.EmployeeExitInterviewRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Single source of truth for when a departing employee's separation is complete.
 *
 * <p>A RESIGNED / TERMINATED / RETIRED employee stays on every list (payroll,
 * timesheet, exit interviews…) until BOTH their exit interview is SUBMITTED and
 * their final-settlement payroll is processed. Only then are they moved to
 * INACTIVE — which is what drops them from the operational lists and makes them
 * archivable. The two steps can happen in either order; whichever finishes last
 * triggers the move.</p>
 */
@Service
@RequiredArgsConstructor
public class EmployeeSeparationService {

    /** Employment-ending statuses that go through exit interview + final settlement. */
    public static final Set<EmployeeStatus> EXIT_STATUSES =
            EnumSet.of(EmployeeStatus.RESIGNED, EmployeeStatus.TERMINATED, EmployeeStatus.RETIRED);

    private final EmployeeRepository employeeRepo;
    private final EmployeeExitInterviewRepository interviewRepo;
    private final PayrollRepository payrollRepo;

    public boolean isExitInterviewSubmitted(Employee employee) {
        return employee != null && employee.getId() != null
                && interviewRepo.findByEmployee_Id(employee.getId())
                        .map(i -> "SUBMITTED".equalsIgnoreCase(i.getStatus()))
                        .orElse(false);
    }

    public boolean isFinalSettlementProcessed(Employee employee) {
        return employee != null && payrollRepo.existsByEmployeeAndFinalSettlementTrue(employee);
    }

    /**
     * Moves an exiting employee to INACTIVE once both separation steps are done.
     * No-op for any other status or while a step is still outstanding.
     */
    @Transactional
    public void completeIfReady(Employee employee) {
        if (employee == null || !EXIT_STATUSES.contains(employee.getStatus())) {
            return;
        }
        if (isExitInterviewSubmitted(employee) && isFinalSettlementProcessed(employee)) {
            employee.setStatus(EmployeeStatus.INACTIVE);
            employeeRepo.save(employee);
        }
    }
}
