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

    /* ================= PREVIEW ================= */

    public LeavePreviewDTO previewLeave(
            Long employeeId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate) {

        if (leaveType == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("Leave type and dates are required");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        int totalDays =
                (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        CompanyLeavePolicy policy =
                policyRepo.findByCompanyAndLeaveType(
                                emp.getCompany(), leaveType)
                        .orElseThrow(() -> new RuntimeException("Leave policy not configured"));

        // UNPAID / EMERGENCY (no balance deduction)
        if (!policy.isPaid()) {
            return new LeavePreviewDTO(
                    totalDays,
                    0,
                    0
            );
        }

        EmployeeLeaveBalance balance =
                balanceRepo.findByEmployeeAndLeaveType(emp, leaveType)
                        .orElseThrow(() ->
                                new RuntimeException("Leave balance not initialized"));

        int remainingAfter = balance.getRemainingLeaves() - totalDays;

        if (remainingAfter < 0) {
            throw new RuntimeException("Insufficient leave balance");
        }

        return new LeavePreviewDTO(
                totalDays,
                balance.getRemainingLeaves(),
                remainingAfter
        );
    }

    /* ================= APPLY (AUTO APPROVE) ================= */

    @Transactional
    public LeaveHistoryDTO applyLeave(Long employeeId, LeaveRequestDTO dto) {

        if (dto.getLeaveType() == null
                || dto.getStartDate() == null
                || dto.getEndDate() == null) {
            throw new IllegalArgumentException("Leave type and dates are required");
        }

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        int totalDays =
                (int) ChronoUnit.DAYS.between(
                        dto.getStartDate(), dto.getEndDate()) + 1;

        CompanyLeavePolicy policy =
                policyRepo.findByCompanyAndLeaveType(
                                emp.getCompany(), dto.getLeaveType())
                        .orElseThrow(() ->
                                new RuntimeException("Leave policy not configured"));

        // PAID LEAVES → deduct balance
        if (policy.isPaid()) {

            EmployeeLeaveBalance balance =
                    balanceRepo.findByEmployeeAndLeaveType(
                                    emp, dto.getLeaveType())
                            .orElseThrow(() ->
                                    new RuntimeException("Leave balance not initialized"));

            if (balance.getRemainingLeaves() < totalDays) {
                throw new RuntimeException("Insufficient leave balance");
            }

            balance.setRemainingLeaves(
                    balance.getRemainingLeaves() - totalDays);

            balanceRepo.save(balance);
        }

        // SAVE HISTORY (AUTO APPROVED)
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
        return null;
    }

    /* ================= HISTORY ================= */

    public List<LeaveHistoryDTO> history(Long employeeId) {

        employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return leaveRepo
                .findByEmployeeIdOrderByDateReportedDesc(employeeId)
                .stream()
                .map(l -> {
                    LeaveHistoryDTO dto = new LeaveHistoryDTO();
                    dto.setLeaveCode(l.getLeaveCode());
                    dto.setLeaveType(l.getLeaveType());
                    dto.setStartDate(l.getStartDate());
                    dto.setEndDate(l.getEndDate());
                    dto.setDateReported(l.getDateReported());
                    dto.setTotalDays(l.getTotalDays());
                    dto.setLeaveStatus(l.getLeaveStatus());
                    return dto;
                })
                .toList();
    }
}
