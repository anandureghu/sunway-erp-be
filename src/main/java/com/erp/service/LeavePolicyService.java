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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final CompanyRepository companyRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;

    /* =====================================================
       GET ALL POLICIES
    ===================================================== */

    public List<LeavePolicyResponseDTO> getAllPolicies(Long companyId) {

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return policyRepo.findByCompany(company)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /* =====================================================
       SAVE / UPDATE POLICIES
    ===================================================== */

    @Transactional
    public void savePolicies(Long companyId,
                             List<LeavePolicyRequestDTO> dtos) {

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (dtos == null || dtos.isEmpty())
            return;

        // Remove duplicates from request
        Map<String, LeavePolicyRequestDTO> unique =
                dtos.stream()
                        .filter(dto -> dto.getRole() != null && dto.getLeaveType() != null)
                        .collect(Collectors.toMap(
                                dto -> dto.getRole().trim() + "_" + dto.getLeaveType().trim(),
                                dto -> dto,
                                (existing, duplicate) -> duplicate
                        ));

        for (LeavePolicyRequestDTO dto : unique.values()) {

            String role = dto.getRole().trim();
            String leaveType = dto.getLeaveType().trim();

            CompanyLeavePolicy policy =
                    policyRepo.findByCompanyAndRoleAndLeaveType(company, role, leaveType)
                            .orElseGet(() -> {
                                CompanyLeavePolicy p = new CompanyLeavePolicy();
                                p.setCompany(company);
                                p.setRole(role);
                                p.setLeaveType(leaveType);
                                return p;
                            });

            policy.setDefaultDays(dto.getDefaultDays() != null ? dto.getDefaultDays() : 0);
            policy.setPaid(dto.getPaid() != null ? dto.getPaid() : true);
            policy.setGenderRestricted(dto.getGenderRestricted() != null ? dto.getGenderRestricted() : false);
            policy.setAllowedGender(dto.getAllowedGender());

            policyRepo.save(policy);

            // Sync balances after every update
            syncBalances(company, policy);
        }
    }

    /* =====================================================
       SYNC BALANCES
    ===================================================== */

    private void syncBalances(Company company, CompanyLeavePolicy policy) {

        if (!Boolean.TRUE.equals(policy.getPaid()))
            return;

        List<Employee> employees = employeeRepo.findByCompany(company);

        for (Employee employee : employees) {

            if (employee.getRole() == null ||
                    !employee.getRole().equals(policy.getRole()))
                continue;

            // Gender restriction check
            if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                if (employee.getGender() == null ||
                        !employee.getGender()
                                .equalsIgnoreCase(policy.getAllowedGender()))
                    continue;
            }

            Optional<EmployeeLeaveBalance> optional =
                    balanceRepo.findByEmployeeAndLeaveType(employee, policy.getLeaveType());

            int newTotal = policy.getDefaultDays();

            if (optional.isPresent()) {

                EmployeeLeaveBalance balance = optional.get();

                int used = balance.getTotalLeaves() - balance.getRemainingLeaves();

                balance.setTotalLeaves(newTotal);

                int recalculatedRemaining = newTotal - used;
                balance.setRemainingLeaves(Math.max(recalculatedRemaining, 0));

                balanceRepo.save(balance);

            } else {

                EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(policy.getLeaveType());
                balance.setTotalLeaves(newTotal);
                balance.setRemainingLeaves(newTotal);

                balanceRepo.save(balance);
            }
        }
    }

    /* =====================================================
       INITIALIZE FOR NEW EMPLOYEE
    ===================================================== */

    @Transactional
    public void initializeLeaveBalancesForEmployee(Employee employee) {

        if (employee.getRole() == null)
            return;

        Company company = employee.getCompany();

        List<CompanyLeavePolicy> policies =
                policyRepo.findByCompanyAndRole(company, employee.getRole());

        for (CompanyLeavePolicy policy : policies) {

            if (!Boolean.TRUE.equals(policy.getPaid()))
                continue;

            if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                if (employee.getGender() == null ||
                        !employee.getGender()
                                .equalsIgnoreCase(policy.getAllowedGender()))
                    continue;
            }

            if (balanceRepo.findByEmployeeAndLeaveType(
                    employee, policy.getLeaveType()).isEmpty()) {

                EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(policy.getLeaveType());
                balance.setTotalLeaves(policy.getDefaultDays());
                balance.setRemainingLeaves(policy.getDefaultDays());

                balanceRepo.save(balance);
            }
        }
    }

    /* =====================================================
       DELETE POLICY (SAFE)
    ===================================================== */

    @Transactional
    public void deletePolicy(Long policyId) {

        CompanyLeavePolicy policy =
                policyRepo.findById(policyId)
                        .orElseThrow(() -> new RuntimeException("Policy not found"));

        List<Employee> employees =
                employeeRepo.findByCompany(policy.getCompany());

        for (Employee employee : employees) {

            if (employee.getRole() == null ||
                    !employee.getRole().equals(policy.getRole()))
                continue;

            balanceRepo.findByEmployeeAndLeaveType(
                            employee, policy.getLeaveType())
                    .ifPresent(balanceRepo::delete);
        }

        policyRepo.delete(policy);
    }

    /* =====================================================
       DTO MAPPER
    ===================================================== */

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
}
