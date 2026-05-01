package com.erp.service;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.domain.User;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.erp.domain.security.HrAction.APPROVE;
import static com.erp.domain.security.HrModule.LEAVES;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final EmployeeRepository employeeRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final EmployeeLeaveRepository leaveRepo;
    private final UserRepository userRepo;
    private final AuthContext authContext;
    private final FileStorageService fileStorageService;
    private final PermissionCheckService permissionCheckService;

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

    public LeavePreviewDTO previewLeave(
            Long employeeId,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            boolean includeWeekends) {

        validateDates(leaveType, startDate, endDate);

        Employee employee = getEmployee(employeeId);
        int totalDays = calculateDays(startDate, endDate, includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, leaveType);
        validateGender(policy, employee);

        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return new LeavePreviewDTO(totalDays, 0, 0);
        }

        EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
        int remainingBefore = balance.getRemainingLeaves();
        int remainingAfter = remainingBefore - totalDays;

        if (remainingAfter < 0) {
            throw new RuntimeException("Insufficient leave balance");
        }

        return new LeavePreviewDTO(totalDays, remainingBefore, remainingAfter);
    }

    @Transactional
    public LeaveHistoryDTO applyLeave(Long employeeId, LeaveRequestDTO dto, MultipartFile supportingDocument) {
        validateDates(dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

        Employee employee = getEmployee(employeeId);
        boolean includeWeekends = Boolean.TRUE.equals(dto.getIncludeWeekends());
        int totalDays = calculateDays(dto.getStartDate(), dto.getEndDate(), includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, dto.getLeaveType());
        validateGender(policy, employee);
        validateSupportingDocument(policy.getLeaveType(), supportingDocument);

        if (Boolean.TRUE.equals(policy.getPaid())) {
            EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
            if (balance.getRemainingLeaves() < totalDays) {
                throw new RuntimeException("Insufficient leave balance");
            }
        }

        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(employee);
        leave.setLeaveCode("L" + System.currentTimeMillis());
        leave.setLeaveType(policy.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setDateReported(LocalDate.now());
        leave.setTotalDays(totalDays);
        leave.setIncludeWeekends(includeWeekends);
        leave.setLeaveStatus("PENDING");

        leave = leaveRepo.save(leave);

        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            FileUploadResult uploadResult = fileStorageService.upload(
                    supportingDocument,
                    FileCategory.LEAVE_SUPPORTING_DOCUMENT,
                    leave.getId().toString(),
                    true
            );
            leave.setSupportingDocumentPath(uploadResult.getBlobPath());
            leave = leaveRepo.save(leave);
        }

        return mapToHistoryDTO(leave);
    }

    public List<LeaveHistoryDTO> history(Long employeeId) {
        return leaveRepo.findByEmployeeIdOrderByDateReportedDesc(employeeId)
                .stream()
                .map(this::mapToHistoryDTO)
                .toList();
    }

    public List<LeaveHistoryDTO> getPendingApprovalsForCurrentApprover() {
        Employee approver = getCurrentEmployee();

        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to view pending leave approvals");
        }

        return leaveRepo.findByLeaveStatusOrderByDateReportedDesc("PENDING")
                .stream()
                .filter(leave -> leave.getEmployee() != null)
                .filter(leave -> leave.getEmployee().getId() != null)
                .filter(leave -> approver.getId() == null || !approver.getId().equals(leave.getEmployee().getId()))
                .filter(leave -> canApproveLeave(approver, leave))
                .map(this::mapToHistoryDTO)
                .toList();
    }

    @Transactional
    public LeaveHistoryDTO approveLeave(Long leaveId) {
        Employee approver = getCurrentEmployee();

        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to approve leave");
        }

        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (leave.getEmployee() == null) {
            throw new RuntimeException("Leave request employee not found");
        }

        if (!"PENDING".equalsIgnoreCase(leave.getLeaveStatus())) {
            throw new IllegalArgumentException("Only pending leave requests can be approved");
        }

        if (!canApproveLeave(approver, leave)) {
            throw new AccessDeniedException(
                    "Access denied: only HR manager or the employee's department manager can approve this leave"
            );
        }

        CompanyLeavePolicy policy = getPolicy(leave.getEmployee(), leave.getLeaveType());

        if (Boolean.TRUE.equals(policy.getPaid())) {
            EmployeeLeaveBalance balance = getOrCreateBalance(leave.getEmployee(), policy);

            if (balance.getRemainingLeaves() < leave.getTotalDays()) {
                throw new RuntimeException("Insufficient leave balance");
            }

            balance.setRemainingLeaves(balance.getRemainingLeaves() - leave.getTotalDays());
            balanceRepo.save(balance);
        }

        leave.setLeaveStatus("APPROVED");
        leave = leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    private Employee getEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    private Employee getCurrentEmployee() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        User currentUser = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return employeeRepo.findByUser_Id(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Employee record not found"));
    }

    private boolean canActAsApprover(Employee approver) {
        return isHrApprover(approver) || isDepartmentManager(approver);
    }

    private boolean canApproveLeave(Employee approver, EmployeeLeave leave) {
        if (leave == null || leave.getEmployee() == null) {
            return false;
        }

        if (hasApprovePermission() && sameCompany(approver, leave.getEmployee())) {
            return true;
        }

        Employee departmentManager = leave.getEmployee().getDepartment() != null
                ? leave.getEmployee().getDepartment().getManager()
                : null;

        return departmentManager != null
                && approver.getId() != null
                && approver.getId().equals(departmentManager.getId());
    }

    private boolean isDepartmentManager(Employee approver) {
        return approver != null
                && approver.getDepartment() != null
                && approver.getDepartment().getManager() != null
                && approver.getId() != null
                && approver.getId().equals(approver.getDepartment().getManager().getId());
    }

    private boolean isHrApprover(Employee approver) {
        return approver != null && hasApprovePermission();
    }

    private boolean hasApprovePermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return permissionCheckService.hasAccess(auth, LEAVES, APPROVE);
    }

    private boolean sameCompany(Employee left, Employee right) {
        return left != null
                && right != null
                && left.getCompany() != null
                && right.getCompany() != null
                && left.getCompany().getId() != null
                && left.getCompany().getId().equals(right.getCompany().getId());
    }

    private int calculateDays(LocalDate start, LocalDate end, boolean includeWeekends) {
        if (includeWeekends) {
            return (int) ChronoUnit.DAYS.between(start, end) + 1;
        }

        int days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!isWeekend(date)) {
                days++;
            }
        }
        return days;
    }

    private void validateDates(String leaveType, LocalDate start, LocalDate end) {
        if (clean(leaveType) == null || start == null || end == null) {
            throw new IllegalArgumentException("Leave type and dates are required");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private CompanyLeavePolicy getPolicy(Employee employee, String leaveType) {
        String role = getLeaveRole(employee);

        if (role == null) {
            throw new RuntimeException("Employee company role not configured");
        }

        return policyRepo.findByCompany(employee.getCompany())
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .filter(policy -> same(policy.getLeaveType(), leaveType))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException("Leave policy not configured for your role and leave type"));
    }

    private void validateGender(CompanyLeavePolicy policy, Employee employee) {
        if (!Boolean.TRUE.equals(policy.getGenderRestricted())) {
            return;
        }

        String employeeGender = clean(employee.getGender());

        if (employeeGender == null || !same(employeeGender, policy.getAllowedGender())) {
            throw new RuntimeException("This leave type is not allowed for your gender");
        }
    }

    private EmployeeLeaveBalance getOrCreateBalance(Employee employee, CompanyLeavePolicy policy) {
        return findBalance(employee, policy.getLeaveType())
                .orElseGet(() -> {
                    EmployeeLeaveBalance newBalance = new EmployeeLeaveBalance();
                    newBalance.setEmployee(employee);
                    newBalance.setLeaveType(balanceKey(policy.getLeaveType()));
                    newBalance.setTotalLeaves(policy.getDefaultDays());
                    newBalance.setRemainingLeaves(policy.getDefaultDays());
                    return balanceRepo.save(newBalance);
                });
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
        dto.setId(leave.getId());
        dto.setLeaveId(leave.getId());
        dto.setEmployeeId(leave.getEmployee() != null ? leave.getEmployee().getId() : null);
        dto.setEmployeeName(leave.getEmployee() != null
                ? (leave.getEmployee().getFirstName() + " " + leave.getEmployee().getLastName()).trim()
                : null);
        dto.setLeaveCode(leave.getLeaveCode());
        dto.setLeaveType(leave.getLeaveType());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setDateReported(leave.getDateReported());
        dto.setTotalDays(leave.getTotalDays());
        dto.setIncludeWeekends(Boolean.TRUE.equals(leave.getIncludeWeekends()));
        dto.setSupportingDocumentUrl(
                leave.getSupportingDocumentPath() != null
                        ? fileStorageService.getPublicUrl(leave.getSupportingDocumentPath())
                        : null
        );
        dto.setLeaveStatus(leave.getLeaveStatus());
        return dto;
    }

    private void validateSupportingDocument(String leaveType, MultipartFile supportingDocument) {
        if (supportingDocument == null || supportingDocument.isEmpty()) {
            return;
        }

        if (!isSickLeave(leaveType)) {
            throw new IllegalArgumentException("Supporting document upload is allowed only for sick leave");
        }
    }

    private boolean isSickLeave(String leaveType) {
        String normalized = key(leaveType);
        return normalized != null && normalized.contains("SICK");
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private String getLeaveRole(Employee employee) {
        if (employee.getCompanyRole() != null) {
            String companyRoleName = clean(employee.getCompanyRole());
            if (companyRoleName != null) {
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