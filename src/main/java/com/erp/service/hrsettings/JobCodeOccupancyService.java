package com.erp.service.hrsettings;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.enums.JobCodeStatus;
import com.erp.domain.hrsettings.JobCode;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.service.CurrentJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps job-code status in step with who holds each code:
 * <ul>
 *   <li>held by someone still on the roster → OCCUPIED (any hold is cleared);</li>
 *   <li>OCCUPIED but the holder left or became inactive → VACANT;</li>
 *   <li>VACANT / ON_HOLD with no holder → unchanged (that is HR's choice).</li>
 * </ul>
 * Called right after assignments and status changes, and before job codes are listed,
 * so the status is never stale.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class JobCodeOccupancyService {

    private final JobCodeRepository jobCodeRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;

    /** Re-evaluates one job code. Returns the holder's name when it is occupied. */
    public String sync(JobCode jobCode) {
        if (jobCode == null || jobCode.getId() == null) {
            return null;
        }
        List<EmployeeCurrentJob> holders = currentJobRepo.findActiveHoldersOfJobCode(
                jobCode.getId(), CurrentJobService.JOB_CODE_FREED_STATUSES);
        String holder = holders.isEmpty() ? null : holderName(holders.get(0).getEmployee());
        if (apply(jobCode, holder != null)) {
            jobCodeRepo.save(jobCode);
        }
        return holder;
    }

    /** Re-evaluates the job code the employee currently holds (after a status change). */
    public void syncForEmployee(Employee employee) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        currentJobRepo.findByEmployee_Id(employee.getId())
                .map(EmployeeCurrentJob::getJobCode)
                .ifPresent(this::sync);
    }

    /**
     * Before a departed / inactive employee comes back onto the roster: their old job
     * code must not have been given to someone else in the meantime. (A code put on
     * hold is fine — they simply occupy it again.)
     */
    @Transactional(readOnly = true)
    public void assertCanRetake(Employee employee) {
        currentJobRepo.findByEmployee_Id(employee.getId()).ifPresent(cj -> {
            JobCode code = cj.getJobCode();
            if (code == null) {
                return;
            }
            currentJobRepo.findActiveHoldersOfJobCode(code.getId(), CurrentJobService.JOB_CODE_FREED_STATUSES)
                    .stream()
                    .filter(h -> !h.getEmployee().getId().equals(employee.getId()))
                    .findFirst()
                    .ifPresent(h -> {
                        throw new IllegalStateException("Job code " + code.getCode()
                                + " is now occupied by " + holderName(h.getEmployee())
                                + ". Assign this employee a vacant job code in Current Job first.");
                    });
        });
    }

    /**
     * Re-evaluates every job code in the company. Returns jobCodeId → holder name for
     * the occupied ones.
     */
    public Map<Long, String> syncCompany(Long companyId) {
        Map<Long, String> holders = new HashMap<>();
        for (EmployeeCurrentJob cj : currentJobRepo.findActiveHoldersInCompany(
                companyId, CurrentJobService.JOB_CODE_FREED_STATUSES)) {
            holders.putIfAbsent(cj.getJobCode().getId(), holderName(cj.getEmployee()));
        }
        for (JobCode jobCode : jobCodeRepo.findByCompany_Id(companyId)) {
            if (apply(jobCode, holders.containsKey(jobCode.getId()))) {
                jobCodeRepo.save(jobCode);
            }
        }
        return holders;
    }

    /** Applies the occupancy rule; true when the code changed. */
    private boolean apply(JobCode jobCode, boolean held) {
        JobCodeStatus current = jobCode.getStatus();
        if (held) {
            if (current == JobCodeStatus.OCCUPIED) {
                return false;
            }
            jobCode.setStatus(JobCodeStatus.OCCUPIED);
            jobCode.setHoldComment(null);
            jobCode.setHoldSince(null);
            jobCode.setHoldReason(null);
            return true;
        }
        if (current == JobCodeStatus.OCCUPIED || current == null) {
            jobCode.setStatus(JobCodeStatus.VACANT);
            return true;
        }
        return false;
    }

    public static String holderName(Employee e) {
        if (e == null) return "another employee";
        String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
        if (!name.isEmpty()) return name;
        return e.getEmployeeNo() != null ? e.getEmployeeNo() : ("employee #" + e.getId());
    }
}
