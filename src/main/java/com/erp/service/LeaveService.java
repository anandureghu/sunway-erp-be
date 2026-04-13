package com.erp.service;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final EmployeeRepository employeeRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final EmployeeLeaveRepository leaveRepo;

    public List<String> getAvailableLeaveTypes(Long employeeId) {
        Employee emp = getEmployee(employeeId);

        String role = getLeaveRole(emp);
        if (role == null) {
            log.warn("Employee {} has no leave role configured", employeeId);
            return List.of();
        }

        return policyRepo.findByCompany(emp.getCompany())
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .filter(policy -> {
                    if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                        String employeeGender = clean(emp.getGender());
                        return employeeGender != null && same(employeeGender, policy.getAllowedGender());
                    }
                    return true;
                })
                .map(CompanyLeavePolicy::getLeaveType)
                .distinct()
                .toList();
    }

    public CompanyLeavePolicy getLeavePolicyDetails(Long employeeId, String leaveType) {
        Employee emp = getEmployee(employeeId);

        return getPolicy(emp, leaveType);
    }

    public LeavePreviewDTO previewLeave(
            Long employeeId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate) {

        validateDates(leaveType, startDate, endDate);

        Employee emp = getEmployee(employeeId);
        int totalDays = calculateDays(startDate, endDate);

        CompanyLeavePolicy policy = getPolicy(emp, leaveType);
        validateGender(policy, emp);

        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return new LeavePreviewDTO(totalDays, 0, 0);
        }

        EmployeeLeaveBalance balance = getOrFetchBalance(emp, policy);

        int remainingBefore = balance.getRemainingLeaves();
        int remainingAfter = remainingBefore - totalDays;

        if (remainingAfter < 0) {
            throw new RuntimeException("Insufficient leave balance");
        }

        return new LeavePreviewDTO(totalDays, remainingBefore, remainingAfter);
    }

    @Transactional
    public LeaveHistoryDTO applyLeave(Long employeeId, LeaveRequestDTO dto) {
        validateDates(dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

        Employee emp = getEmployee(employeeId);
        int totalDays = calculateDays(dto.getStartDate(), dto.getEndDate());

        CompanyLeavePolicy policy = getPolicy(emp, dto.getLeaveType());
        validateGender(policy, emp);

        if (Boolean.TRUE.equals(policy.getPaid())) {
            EmployeeLeaveBalance balance = getOrFetchBalance(emp, policy);

            if (balance.getRemainingLeaves() < totalDays) {
                throw new RuntimeException("Insufficient leave balance");
            }

            balance.setRemainingLeaves(balance.getRemainingLeaves() - totalDays);
            balanceRepo.save(balance);
        }

        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(emp);
        leave.setLeaveCode("L" + System.currentTimeMillis());
        leave.setLeaveType(policy.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setDateReported(LocalDate.now());
        leave.setTotalDays(totalDays);
        leave.setLeaveStatus("APPROVED");

        leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    public List<LeaveHistoryDTO> history(Long employeeId) {
        return leaveRepo.findByEmployeeIdOrderByDateReportedDesc(employeeId)
                .stream()
                .map(this::mapToHistoryDTO)
                .toList();
    }

    private Employee getEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    private int calculateDays(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void validateDates(String leaveType, LocalDate start, LocalDate end) {
        if (clean(leaveType) == null || start == null || end == null) {
            throw new IllegalArgumentException("Leave type and dates are required");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private CompanyLeavePolicy getPolicy(Employee emp, String leaveType) {
        String role = getLeaveRole(emp);

        if (role == null) {
            throw new RuntimeException("Employee company role not configured");
        }

        return policyRepo.findByCompany(emp.getCompany())
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .filter(policy -> same(policy.getLeaveType(), leaveType))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Leave policy not configured for your role and leave type"));
    }

    private void validateGender(CompanyLeavePolicy policy, Employee emp) {
        if (!Boolean.TRUE.equals(policy.getGenderRestricted())) {
            return;
        }

        String employeeGender = clean(emp.getGender());

        if (employeeGender == null || !same(employeeGender, policy.getAllowedGender())) {
            throw new RuntimeException("This leave type is not allowed for your gender");
        }
    }

    private EmployeeLeaveBalance getOrFetchBalance(Employee emp, CompanyLeavePolicy policy) {
        Optional<EmployeeLeaveBalance> balanceOpt = findBalance(emp, policy.getLeaveType());

        if (balanceOpt.isPresent()) {
            return balanceOpt.get();
        }

        EmployeeLeaveBalance newBalance = new EmployeeLeaveBalance();
        newBalance.setEmployee(emp);
        newBalance.setLeaveType(balanceKey(policy.getLeaveType()));
        newBalance.setTotalLeaves(policy.getDefaultDays());
        newBalance.setRemainingLeaves(policy.getDefaultDays());

        return balanceRepo.save(newBalance);
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

    private LeaveHistoryDTO mapToHistoryDTO(EmployeeLeave leave) {
        LeaveHistoryDTO dto = new LeaveHistoryDTO();
        dto.setLeaveCode(leave.getLeaveCode());
        dto.setLeaveType(leave.getLeaveType());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setDateReported(leave.getDateReported());
        dto.setTotalDays(leave.getTotalDays());
        dto.setLeaveStatus(leave.getLeaveStatus());
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
