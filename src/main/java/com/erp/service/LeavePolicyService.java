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

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    private final CompanyRepository companyRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;

    /* ========= GET ALL POLICIES ========= */
    public List<LeavePolicyResponseDTO> getAllPolicies(Long companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return policyRepo.findByCompany(company)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /* ========= CREATE NEW POLICY ========= */
    @Transactional
    public LeavePolicyResponseDTO createPolicy(Long companyId, LeavePolicyRequestDTO dto) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Check if policy already exists
        if (policyRepo.findByCompanyAndLeaveType(company, dto.getLeaveType()).isPresent()) {
            throw new RuntimeException("Leave policy already exists for this type");
        }

        CompanyLeavePolicy policy = new CompanyLeavePolicy();
        policy.setCompany(company);
        policy.setLeaveType(dto.getLeaveType());
        policy.setPaid(dto.isPaid());
        policy.setDefaultDays(dto.getDefaultDays());

        policy = policyRepo.save(policy);

        // If it's a paid leave, initialize balances for all existing employees
        if (dto.isPaid()) {
            initializeBalancesForAllEmployees(company, policy);
        }

        return toDTO(policy);
    }

    /* ========= UPDATE EXISTING POLICY ========= */
    @Transactional
    public LeavePolicyResponseDTO updatePolicy(Long policyId, LeavePolicyRequestDTO dto) {
        CompanyLeavePolicy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Leave policy not found"));

        boolean wasUnpaid = !policy.isPaid();
        boolean nowPaid = dto.isPaid();

        int oldDefaultDays = policy.getDefaultDays();
        int newDefaultDays = dto.getDefaultDays();

        // Update policy
        policy.setLeaveType(dto.getLeaveType());
        policy.setPaid(dto.isPaid());
        policy.setDefaultDays(dto.getDefaultDays());
        policy = policyRepo.save(policy);

        // Handle balance updates
        if (wasUnpaid && nowPaid) {
            // Changed from unpaid to paid - create balances for all employees
            initializeBalancesForAllEmployees(policy.getCompany(), policy);
        } else if (!wasUnpaid && !nowPaid) {
            // Still paid - update existing balances if default days changed
            if (oldDefaultDays != newDefaultDays) {
                updateExistingBalances(policy.getCompany(), policy, oldDefaultDays, newDefaultDays);
            }
        } else if (!wasUnpaid && nowPaid) {
            // Changed from paid to unpaid - optionally delete balances
            deleteBalancesForLeaveType(policy.getCompany(), policy.getLeaveType());
        }

        return toDTO(policy);
    }

    /* ========= DELETE POLICY ========= */
    @Transactional
    public void deletePolicy(Long policyId) {
        CompanyLeavePolicy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Leave policy not found"));

        // Delete all employee balances for this leave type
        deleteBalancesForLeaveType(policy.getCompany(), policy.getLeaveType());

        // Delete the policy
        policyRepo.delete(policy);
    }

    /* ========= INITIALIZE DEFAULT POLICIES ========= */
    @Transactional
    public void initializeDefaultPoliciesForCompany(Long companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Check if policies already exist
        List<CompanyLeavePolicy> existing = policyRepo.findByCompany(company);
        if (!existing.isEmpty()) {
            throw new RuntimeException("Leave policies already configured for this company");
        }

        // Create default policies
        createDefaultPolicy(company, "Annual Leave", true, 20);
        createDefaultPolicy(company, "Sick Leave", true, 10);
        createDefaultPolicy(company, "Casual Leave", true, 5);
        createDefaultPolicy(company, "Emergency Leave", false, 0);
        createDefaultPolicy(company, "Unpaid Leave", false, 0);
    }

    /* ========= HELPER METHODS ========= */

    private void createDefaultPolicy(Company company, String leaveType, boolean isPaid, int defaultDays) {
        CompanyLeavePolicy policy = new CompanyLeavePolicy();
        policy.setCompany(company);
        policy.setLeaveType(leaveType);
        policy.setPaid(isPaid);
        policy.setDefaultDays(defaultDays);
        policy = policyRepo.save(policy);

        if (isPaid) {
            initializeBalancesForAllEmployees(company, policy);
        }
    }

    private void initializeBalancesForAllEmployees(Company company, CompanyLeavePolicy policy) {
        List<Employee> employees = employeeRepo.findByCompany(company);

        for (Employee employee : employees) {
            // Check if balance already exists
            if (balanceRepo.findByEmployeeAndLeaveType(employee, policy.getLeaveType()).isEmpty()) {
                EmployeeLeaveBalance balance = new EmployeeLeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(policy.getLeaveType());
                balance.setTotalLeaves(policy.getDefaultDays());
                balance.setRemainingLeaves(policy.getDefaultDays());
                balanceRepo.save(balance);
            }
        }
    }

    private void updateExistingBalances(Company company, CompanyLeavePolicy policy,
                                        int oldDefaultDays, int newDefaultDays) {
        List<Employee> employees = employeeRepo.findByCompany(company);
        int difference = newDefaultDays - oldDefaultDays;

        for (Employee employee : employees) {
            balanceRepo.findByEmployeeAndLeaveType(employee, policy.getLeaveType())
                    .ifPresent(balance -> {
                        // Add/subtract the difference to both total and remaining
                        balance.setTotalLeaves(balance.getTotalLeaves() + difference);
                        balance.setRemainingLeaves(balance.getRemainingLeaves() + difference);

                        // Ensure remaining doesn't go negative
                        if (balance.getRemainingLeaves() < 0) {
                            balance.setRemainingLeaves(0);
                        }

                        balanceRepo.save(balance);
                    });
        }
    }

    private void deleteBalancesForLeaveType(Company company, String leaveType) {
        List<Employee> employees = employeeRepo.findByCompany(company);

        for (Employee employee : employees) {
            balanceRepo.findByEmployeeAndLeaveType(employee, leaveType)
                    .ifPresent(balanceRepo::delete);
        }
    }

    private LeavePolicyResponseDTO toDTO(CompanyLeavePolicy policy) {
        LeavePolicyResponseDTO dto = new LeavePolicyResponseDTO();
        dto.setId(policy.getId());
        dto.setLeaveType(policy.getLeaveType());
        dto.setPaid(policy.isPaid());
        dto.setDefaultDays(policy.getDefaultDays());
        return dto;
    }
}