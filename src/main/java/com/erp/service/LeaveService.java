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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final EmployeeRepository employeeRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final EmployeeLeaveRepository leaveRepo;

    /* =====================================================
       GET AVAILABLE LEAVE TYPES (ROLE + GENDER BASED)
    ===================================================== */

    public List<String> getAvailableLeaveTypes(Long employeeId) {

        Employee emp = getEmployee(employeeId);

        if (emp.getRole() == null) return List.of();

        return policyRepo.findByCompanyAndRole(
                        emp.getCompany(),
                        emp.getRole()
                ).stream()
                .filter(policy -> {

                    // Gender restriction check
                    if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                        return emp.getGender() != null &&
                                emp.getGender()
                                        .equalsIgnoreCase(policy.getAllowedGender());
                    }

                    return true;
                })
                .map(CompanyLeavePolicy::getLeaveType)
                .toList();
    }

    /* =====================================================
       PREVIEW LEAVE
    ===================================================== */

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

        // UNPAID LEAVE → no balance required
        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return new LeavePreviewDTO(
                    totalDays,
                    0,
                    0
            );
        }

        EmployeeLeaveBalance balance =
                getOrCreateBalance(emp, policy);

        int remainingAfter =
                balance.getRemainingLeaves() - totalDays;

        if (remainingAfter < 0) {
            throw new RuntimeException("Insufficient leave balance");
        }

        return new LeavePreviewDTO(
                totalDays,
                balance.getRemainingLeaves(),
                remainingAfter
        );
    }

    /* =====================================================
       APPLY LEAVE
    ===================================================== */

    @Transactional
    public LeaveHistoryDTO applyLeave(Long employeeId,
                                      LeaveRequestDTO dto) {

        validateDates(
                dto.getLeaveType(),
                dto.getStartDate(),
                dto.getEndDate());

        Employee emp = getEmployee(employeeId);

        int totalDays =
                calculateDays(dto.getStartDate(),
                        dto.getEndDate());

        CompanyLeavePolicy policy =
                getPolicy(emp, dto.getLeaveType());

        validateGender(policy, emp);

        // Paid Leave → Deduct Balance
        if (Boolean.TRUE.equals(policy.getPaid())) {

            EmployeeLeaveBalance balance =
                    getOrCreateBalance(emp, policy);

            if (balance.getRemainingLeaves() < totalDays) {
                throw new RuntimeException("Insufficient leave balance");
            }

            balance.setRemainingLeaves(
                    balance.getRemainingLeaves() - totalDays);

            balanceRepo.save(balance);
        }

        // Save Leave Record
        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(emp);
        leave.setLeaveCode("L" + System.currentTimeMillis());
        leave.setLeaveType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setDateReported(LocalDate.now());
        leave.setTotalDays(totalDays);
        leave.setLeaveStatus("APPROVED");

        leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    /* =====================================================
       HISTORY
    ===================================================== */

    public List<LeaveHistoryDTO> history(Long employeeId) {

        getEmployee(employeeId);

        return leaveRepo
                .findByEmployeeIdOrderByDateReportedDesc(employeeId)
                .stream()
                .map(this::mapToHistoryDTO)
                .toList();
    }

    /* =====================================================
       PRIVATE HELPERS
    ===================================================== */

    private Employee getEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    private int calculateDays(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void validateDates(String leaveType,
                               LocalDate start,
                               LocalDate end) {

        if (leaveType == null || start == null || end == null) {
            throw new IllegalArgumentException("Leave type and dates are required");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private CompanyLeavePolicy getPolicy(Employee emp,
                                         String leaveType) {

        return policyRepo
                .findByCompanyAndRoleAndLeaveType(
                        emp.getCompany(),
                        emp.getRole(),
                        leaveType)
                .orElseThrow(() ->
                        new RuntimeException("Leave policy not configured for your role"));
    }

    private void validateGender(CompanyLeavePolicy policy,
                                Employee emp) {

        if (Boolean.TRUE.equals(policy.getGenderRestricted())) {

            if (emp.getGender() == null ||
                    !emp.getGender()
                            .equalsIgnoreCase(policy.getAllowedGender())) {

                throw new RuntimeException(
                        "This leave type is not allowed for your gender");
            }
        }
    }

    /*
        AUTO CREATE BALANCE IF MISSING
    */
    private EmployeeLeaveBalance getOrCreateBalance(
            Employee emp,
            CompanyLeavePolicy policy) {

        return balanceRepo
                .findByEmployeeAndLeaveType(
                        emp,
                        policy.getLeaveType())
                .orElseGet(() -> {

                    EmployeeLeaveBalance newBalance =
                            new EmployeeLeaveBalance();

                    newBalance.setEmployee(emp);
                    newBalance.setLeaveType(policy.getLeaveType());
                    newBalance.setTotalLeaves(policy.getDefaultDays());
                    newBalance.setRemainingLeaves(policy.getDefaultDays());

                    return balanceRepo.save(newBalance);
                });
    }

    private LeaveHistoryDTO mapToHistoryDTO(Employee leave) {
        return null;
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
}
