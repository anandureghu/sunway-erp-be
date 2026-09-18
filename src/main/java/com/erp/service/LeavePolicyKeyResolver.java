package com.erp.service;

import com.erp.domain.Employee;
import com.erp.repo.EmployeeCurrentJobRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the ordered set of keys used to match an {@link Employee} to a
 * {@link com.erp.domain.CompanyLeavePolicy}.
 *
 * <p>Leave policies are configured per JOB CODE — a company/security role governs
 * system access only, not leave entitlement. We match the employee's current job
 * code first (by its {@code code}, then its {@code title}) and fall back to the
 * company role and the legacy security role, so policies configured before the
 * role&rarr;job-code move keep working with no reconfiguration. This mirrors the
 * appraisal module's role&rarr;job-code resolution and is the single source of truth
 * for leave-policy matching (previously duplicated across four services).</p>
 */
@Component
@RequiredArgsConstructor
public class LeavePolicyKeyResolver {

    private final EmployeeCurrentJobRepo currentJobRepo;

    /**
     * Ordered, de-duplicated match keys for this employee:
     * {@code [current job code, job title, company role, security role]}.
     * The last two are a legacy bridge for policies saved before the migration.
     */
    public List<String> keysFor(Employee employee) {
        Set<String> keys = new LinkedHashSet<>();
        if (employee != null) {
            if (employee.getId() != null) {
                currentJobRepo.findByEmployee_Id(employee.getId()).ifPresent(cj -> {
                    if (cj.getJobCode() != null) {
                        add(keys, cj.getJobCode().getCode());
                        add(keys, cj.getJobCode().getTitle());
                    }
                });
            }
            add(keys, employee.getCompanyRole());
            add(keys, employee.getRole());
        }
        return List.copyOf(keys);
    }

    /** The employee's current job code string, or {@code null} if none is assigned. */
    public String primaryJobCode(Employee employee) {
        if (employee == null || employee.getId() == null) {
            return null;
        }
        return currentJobRepo.findByEmployee_Id(employee.getId())
                .map(cj -> cj.getJobCode() != null ? cj.getJobCode().getCode() : null)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .orElse(null);
    }

    private void add(Set<String> keys, String value) {
        if (value != null) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed);
            }
        }
    }
}
