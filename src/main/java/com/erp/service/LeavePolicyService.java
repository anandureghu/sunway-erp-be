package com.erp.service;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.domain.hr.Company;
import com.erp.dto.leave.LeavePolicyRequestDTO;
import com.erp.dto.leave.LeavePolicyResponseDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final CompanyRepository companyRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final AuthContext authContext;
    private final LeavePolicyKeyResolver keyResolver;

    public List<LeavePolicyResponseDTO> getAllPolicies(Long companyId) {
        assertSameTenant(companyId);
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return policyRepo.findByCompanyOrderByIdDesc(company)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<LeavePolicyResponseDTO> getPoliciesByJobCode(Long companyId, String jobCode) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        String cleanJobCode = clean(jobCode);
        if (cleanJobCode == null || cleanJobCode.isBlank()) {
            throw new IllegalArgumentException("Job code cannot be null or empty");
        }

        return findPoliciesByJobCode(company, cleanJobCode)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public void savePolicies(Long companyId, List<LeavePolicyRequestDTO> dtos) {
        assertSameTenant(companyId);
        savePoliciesInternal(companyId, dtos);
    }

    /**
     * Same as {@link #savePolicies} but skips the tenant guard — used by system
     * bootstrap / company-create when no authenticated company context exists.
     */
    @Transactional
    public void savePoliciesAsSystem(Long companyId, List<LeavePolicyRequestDTO> dtos) {
        savePoliciesInternal(companyId, dtos);
    }

    private void savePoliciesInternal(Long companyId, List<LeavePolicyRequestDTO> dtos) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (dtos == null || dtos.isEmpty()) {
            return;
        }

        Map<String, LeavePolicyRequestDTO> unique = new LinkedHashMap<>();

        for (LeavePolicyRequestDTO dto : dtos) {
            String jobCode = clean(dto.getJobCode());
            String leaveType = clean(dto.getLeaveType());

            if (jobCode == null || leaveType == null) {
                continue;
            }

            unique.put(key(jobCode) + "_" + key(leaveType), dto);
        }

        for (LeavePolicyRequestDTO dto : unique.values()) {
            String jobCode = clean(dto.getJobCode());
            String leaveType = clean(dto.getLeaveType());
            String allowedGender = clean(dto.getAllowedGender());
            String allowedReligion = clean(dto.getAllowedReligion());

            CompanyLeavePolicy policy = findPolicy(company, jobCode, leaveType)
                    .orElseGet(() -> {
                        CompanyLeavePolicy p = new CompanyLeavePolicy();
                        p.setCompany(company);
                        return p;
                    });

            policy.setJobCode(jobCode);
            policy.setLeaveType(leaveType);
            policy.setDefaultDays(dto.getDefaultDays() != null ? dto.getDefaultDays() : 0);
            policy.setPaid(dto.getPaid() != null ? dto.getPaid() : true);
            policy.setGenderRestricted(dto.getGenderRestricted() != null ? dto.getGenderRestricted() : false);
            policy.setAllowedGender(Boolean.TRUE.equals(policy.getGenderRestricted()) ? allowedGender : null);
            policy.setReligionRestricted(dto.getReligionRestricted() != null ? dto.getReligionRestricted() : false);
            policy.setAllowedReligion(Boolean.TRUE.equals(policy.getReligionRestricted()) ? allowedReligion : null);

            policyRepo.save(policy);
            syncBalances(company, policy);
        }
    }

    private void syncBalances(Company company, CompanyLeavePolicy policy) {
        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return;
        }

        List<Employee> employees = employeeRepo.findByCompanyOrderByCreatedAtDesc(company);

        for (Employee employee : employees) {
            if (!isEffectivePolicyForEmployee(employee, policy)) {
                continue;
            }

            if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                String employeeGender = clean(employee.getGender());
                if (employeeGender == null || !same(employeeGender, policy.getAllowedGender())) {
                    continue;
                }
            }

            if (Boolean.TRUE.equals(policy.getReligionRestricted())) {
                String employeeReligion = clean(employee.getReligion());
                if (employeeReligion == null || !same(employeeReligion, policy.getAllowedReligion())) {
                    continue;
                }
            }

            // When this company runs annual-leave on accrual, the balance is
            // recomputed live from join-date by LeaveService — don't seed it
            // with the policy's static yearly default (would over-credit new
            // hires).
            if (isAccruedAnnualLeavePolicy(company, policy)) {
                continue;
            }

            Optional<EmployeeLeaveBalance> optional = findBalance(employee, policy.getLeaveType());
            int newTotal = policy.getDefaultDays();

            if (optional.isPresent()) {
                EmployeeLeaveBalance balance = optional.get();

                int used = balance.getTotalLeaves() - balance.getRemainingLeaves();

                balance.setLeaveType(balanceKey(policy.getLeaveType()));
                balance.setTotalLeaves(newTotal);
                balance.setRemainingLeaves(Math.max(newTotal - used, 0));

                balanceRepo.save(balance);
            } else {
                EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(balanceKey(policy.getLeaveType()));
                balance.setTotalLeaves(newTotal);
                balance.setRemainingLeaves(newTotal);

                balanceRepo.save(balance);
            }
        }
    }

    @Transactional
    public void initializeLeaveBalancesForEmployee(Employee employee) {
        if (keyResolver.keysFor(employee).isEmpty()) {
            return;
        }

        Company company = employee.getCompany();
        if (company == null) {
            throw new RuntimeException("Employee company cannot be null");
        }

        List<CompanyLeavePolicy> policies = findPoliciesForEmployee(employee);

        for (CompanyLeavePolicy policy : policies) {
            if (!Boolean.TRUE.equals(policy.getPaid())) {
                continue;
            }

            if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                String employeeGender = clean(employee.getGender());
                if (employeeGender == null || !same(employeeGender, policy.getAllowedGender())) {
                    continue;
                }
            }

            if (Boolean.TRUE.equals(policy.getReligionRestricted())) {
                String employeeReligion = clean(employee.getReligion());
                if (employeeReligion == null || !same(employeeReligion, policy.getAllowedReligion())) {
                    continue;
                }
            }

            if (findBalance(employee, policy.getLeaveType()).isPresent()) {
                continue;
            }

            // For accrued annual leave the balance is created lazily by
            // LeaveService at the first preview/apply, reflecting the months
            // worked. Seeding the static default here would over-credit the
            // employee on day one.
            if (isAccruedAnnualLeavePolicy(company, policy)) {
                continue;
            }

            EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
            balance.setEmployee(employee);
            balance.setLeaveType(balanceKey(policy.getLeaveType()));
            balance.setTotalLeaves(policy.getDefaultDays());
            balance.setRemainingLeaves(policy.getDefaultDays());

            balanceRepo.save(balance);
        }
    }

    private boolean isAccruedAnnualLeavePolicy(Company company, CompanyLeavePolicy policy) {
        if (company == null || policy == null) {
            return false;
        }
        if (!company.isAnnualLeaveAccrualEnabled()) {
            return false;
        }
        String normalized = key(policy.getLeaveType());
        return normalized != null && normalized.contains("ANNUAL");
    }

    @Transactional
    public void deletePolicy(Long policyId) {
        CompanyLeavePolicy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        assertSameTenant(policy.getCompany());

        List<Employee> employees = employeeRepo.findByCompanyOrderByCreatedAtDesc(policy.getCompany());

        for (Employee employee : employees) {
            if (!isEffectivePolicyForEmployee(employee, policy)
                    || hasReplacementPolicyForLeaveType(employee, policy)) {
                continue;
            }

            findBalance(employee, policy.getLeaveType()).ifPresent(balanceRepo::delete);
        }

        policyRepo.delete(policy);
    }

    /* ================= TENANT GUARD ================= */

    private void assertSameTenant(Long companyId) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId == null || companyId == null
                || !currentCompanyId.equals(companyId)) {
            throw new AccessDeniedException("This company's leave policies belong to a different company");
        }
    }

    private void assertSameTenant(Company company) {
        assertSameTenant(company != null ? company.getId() : null);
    }

    private List<CompanyLeavePolicy> findPoliciesByJobCode(Company company, String jobCode) {
        return policyRepo.findByCompanyOrderByIdDesc(company)
                .stream()
                .filter(policy -> same(policy.getJobCode(), jobCode))
                .toList();
    }

    private Optional<CompanyLeavePolicy> findPolicy(Company company, String jobCode, String leaveType) {
        return policyRepo.findByCompanyOrderByIdDesc(company)
                .stream()
                .filter(policy -> same(policy.getJobCode(), jobCode))
                .filter(policy -> same(policy.getLeaveType(), leaveType))
                .findFirst();
    }

    private Optional<EmployeeLeaveBalance> findBalance(Employee employee, String leaveType) {
        String canonical = balanceKey(leaveType);
        String raw = clean(leaveType);

        Optional<EmployeeLeaveBalance> balance =
                balanceRepo.findByEmployeeIdAndLeaveType(employee.getId(), canonical);

        if (balance.isEmpty()) {
            balance = balanceRepo.findByEmployeeAndLeaveType(employee, canonical);
        }

        if (balance.isPresent()) {
            EmployeeLeaveBalance found = balance.get();
            if (!canonical.equals(found.getLeaveType())) {
                found.setLeaveType(canonical);
                balanceRepo.save(found);
            }
            return Optional.of(found);
        }

        if (raw != null && !raw.equals(canonical)) {
            balance = balanceRepo.findByEmployeeIdAndLeaveType(employee.getId(), raw);

            if (balance.isEmpty()) {
                balance = balanceRepo.findByEmployeeAndLeaveType(employee, raw);
            }

            if (balance.isPresent()) {
                EmployeeLeaveBalance found = balance.get();
                found.setLeaveType(canonical);
                balanceRepo.save(found);
                return Optional.of(found);
            }
        }

        return Optional.empty();
    }

    private LeavePolicyResponseDTO toDTO(CompanyLeavePolicy policy) {
        LeavePolicyResponseDTO dto = new LeavePolicyResponseDTO();
        dto.setId(policy.getId());
        dto.setJobCode(policy.getJobCode());
        dto.setLeaveType(policy.getLeaveType());
        dto.setDefaultDays(policy.getDefaultDays());
        dto.setPaid(Boolean.TRUE.equals(policy.getPaid()));
        dto.setGenderRestricted(Boolean.TRUE.equals(policy.getGenderRestricted()));
        dto.setAllowedGender(policy.getAllowedGender());
        dto.setReligionRestricted(Boolean.TRUE.equals(policy.getReligionRestricted()));
        dto.setAllowedReligion(policy.getAllowedReligion());
        return dto;
    }

    private List<CompanyLeavePolicy> findPoliciesForEmployee(Employee employee) {
        Map<String, CompanyLeavePolicy> policiesByLeaveType = new LinkedHashMap<>();

        // Job-code policies take precedence; job title / company role / security role
        // are legacy fallbacks (see LeavePolicyKeyResolver).
        for (String matchKey : keyResolver.keysFor(employee)) {
            findPoliciesByJobCode(employee.getCompany(), matchKey)
                    .forEach(policy -> policiesByLeaveType.putIfAbsent(key(policy.getLeaveType()), policy));
        }

        return List.copyOf(policiesByLeaveType.values());
    }

    private boolean isEffectivePolicyForEmployee(Employee employee, CompanyLeavePolicy policy) {
        return policy != null
                && policy.getId() != null
                && findPoliciesForEmployee(employee).stream()
                        .anyMatch(candidate -> policy.getId().equals(candidate.getId()));
    }

    private boolean hasReplacementPolicyForLeaveType(Employee employee, CompanyLeavePolicy policy) {
        List<String> keys = keyResolver.keysFor(employee);
        return policy != null
                && policy.getId() != null
                && policyRepo.findByCompanyOrderByIdDesc(employee.getCompany()).stream()
                        .anyMatch(candidate -> !policy.getId().equals(candidate.getId())
                                && keys.stream()
                                .anyMatch(matchKey -> same(candidate.getJobCode(), matchKey))
                                && same(candidate.getLeaveType(), policy.getLeaveType()));
    }

    private String balanceKey(String value) {
        return key(value);
    }

    private boolean same(String left, String right) {
        String leftKey = key(left);
        String rightKey = key(right);
        return leftKey != null && leftKey.equals(rightKey);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String key(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
