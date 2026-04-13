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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final CompanyRepository companyRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;

    public List<LeavePolicyResponseDTO> getAllPolicies(Long companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return policyRepo.findByCompany(company)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<LeavePolicyResponseDTO> getPoliciesByRole(Long companyId, String role) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        String cleanRole = clean(role);
        if (cleanRole == null || cleanRole.isBlank()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }

        return findPoliciesByRole(company, cleanRole)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public void savePolicies(Long companyId, List<LeavePolicyRequestDTO> dtos) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (dtos == null || dtos.isEmpty()) {
            return;
        }

        Map<String, LeavePolicyRequestDTO> unique = new LinkedHashMap<>();

        for (LeavePolicyRequestDTO dto : dtos) {
            String role = clean(dto.getRole());
            String leaveType = clean(dto.getLeaveType());

            if (role == null || leaveType == null) {
                continue;
            }

            unique.put(key(role) + "_" + key(leaveType), dto);
        }

        for (LeavePolicyRequestDTO dto : unique.values()) {
            String role = clean(dto.getRole());
            String leaveType = clean(dto.getLeaveType());
            String allowedGender = clean(dto.getAllowedGender());

            CompanyLeavePolicy policy = findPolicy(company, role, leaveType)
                    .orElseGet(() -> {
                        CompanyLeavePolicy p = new CompanyLeavePolicy();
                        p.setCompany(company);
                        return p;
                    });

            policy.setRole(role);
            policy.setLeaveType(leaveType);
            policy.setDefaultDays(dto.getDefaultDays() != null ? dto.getDefaultDays() : 0);
            policy.setPaid(dto.getPaid() != null ? dto.getPaid() : true);
            policy.setGenderRestricted(dto.getGenderRestricted() != null ? dto.getGenderRestricted() : false);
            policy.setAllowedGender(Boolean.TRUE.equals(policy.getGenderRestricted()) ? allowedGender : null);

            policyRepo.save(policy);
            syncBalances(company, policy);
        }
    }

    private void syncBalances(Company company, CompanyLeavePolicy policy) {
        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return;
        }

        List<Employee> employees = employeeRepo.findByCompany(company);

        for (Employee employee : employees) {
            String employeeRole = getLeaveRole(employee);

            if (employeeRole == null || !same(employeeRole, policy.getRole())) {
                continue;
            }

            if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                String employeeGender = clean(employee.getGender());
                if (employeeGender == null || !same(employeeGender, policy.getAllowedGender())) {
                    continue;
                }
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
        String employeeRole = getLeaveRole(employee);
        if (employeeRole == null) {
            return;
        }

        Company company = employee.getCompany();
        if (company == null) {
            throw new RuntimeException("Employee company cannot be null");
        }

        List<CompanyLeavePolicy> policies = findPoliciesByRole(company, employeeRole);

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

            if (findBalance(employee, policy.getLeaveType()).isPresent()) {
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

    @Transactional
    public void deletePolicy(Long policyId) {
        CompanyLeavePolicy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        List<Employee> employees = employeeRepo.findByCompany(policy.getCompany());

        for (Employee employee : employees) {
            String employeeRole = getLeaveRole(employee);

            if (employeeRole == null || !same(employeeRole, policy.getRole())) {
                continue;
            }

            findBalance(employee, policy.getLeaveType()).ifPresent(balanceRepo::delete);
        }

        policyRepo.delete(policy);
    }

    private List<CompanyLeavePolicy> findPoliciesByRole(Company company, String role) {
        return policyRepo.findByCompany(company)
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .toList();
    }

    private Optional<CompanyLeavePolicy> findPolicy(Company company, String role, String leaveType) {
        return policyRepo.findByCompany(company)
                .stream()
                .filter(policy -> same(policy.getRole(), role))
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
        dto.setRole(policy.getRole());
        dto.setLeaveType(policy.getLeaveType());
        dto.setDefaultDays(policy.getDefaultDays());
        dto.setPaid(Boolean.TRUE.equals(policy.getPaid()));
        dto.setGenderRestricted(Boolean.TRUE.equals(policy.getGenderRestricted()));
        dto.setAllowedGender(policy.getAllowedGender());
        return dto;
    }

    private String getLeaveRole(Employee employee) {
        if (employee.getCompanyRole() != null && employee.getCompanyRole() != null) {
            String companyRoleName = clean(employee.getCompanyRole());
            if (companyRoleName != null && !companyRoleName.isBlank()) {
                return companyRoleName;
            }
        }

        return clean(employee.getRole());
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
