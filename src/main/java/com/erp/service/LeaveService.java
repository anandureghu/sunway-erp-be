package com.erp.service;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.LeaveStatus;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.leave.LeaveBalanceSummaryDTO;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.security.PermissionCheckService;
import com.erp.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.erp.domain.security.AppAction.APPROVE;
import static com.erp.domain.security.AppModule.LEAVES;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final EmployeeRepository employeeRepo;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final EmployeeLeaveRepository leaveRepo;
    private final UserRepository userRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final AuthContext authContext;
    private final FileStorageService fileStorageService;
    private final PermissionCheckService permissionCheckService;
    private final DocumentSequenceService documentSequenceService;

    public List<String> getAvailableLeaveTypes(Long employeeId) {
        Employee emp = getEmployee(employeeId);

        String role = getLeaveRole(emp);
        if (role == null) {
            log.warn("Employee {} has no leave role configured", employeeId);
            return List.of();
        }

        return policyRepo.findByCompanyOrderByIdDesc(emp.getCompany())
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .filter(policy -> {
                    if (Boolean.TRUE.equals(policy.getGenderRestricted())) {
                        String employeeGender = clean(emp.getGender());
                        return employeeGender != null && same(employeeGender, policy.getAllowedGender());
                    }
                    return true;
                })
                .filter(policy -> {
                    if (Boolean.TRUE.equals(policy.getReligionRestricted())) {
                        String employeeReligion = clean(emp.getReligion());
                        return employeeReligion != null && same(employeeReligion, policy.getAllowedReligion());
                    }
                    return true;
                })
                .map(CompanyLeavePolicy::getLeaveType)
                .distinct()
                .toList();
    }

    public List<LeaveBalanceSummaryDTO> listBalances(Long employeeId) {
        Employee employee = getEmployee(employeeId);

        return getAvailableLeaveTypes(employeeId)
                .stream()
                .distinct()
                .map(leaveType -> {
                    CompanyLeavePolicy policy = getPolicy(employee, leaveType);
                    if (!policy.isPaid()) {
                        return LeaveBalanceSummaryDTO.builder()
                                .leaveType(policy.getLeaveType())
                                .paid(false)
                                .totalLeaves(0)
                                .remainingLeaves(0)
                                .build();
                    }

                    EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
                    return LeaveBalanceSummaryDTO.builder()
                            .leaveType(policy.getLeaveType())
                            .paid(true)
                            .totalLeaves(balance.getTotalLeaves())
                            .remainingLeaves(balance.getRemainingLeaves())
                            .build();
                })
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
        validateReligion(policy, employee);
        enforceAccrualRules(employee, policy);

        if (!Boolean.TRUE.equals(policy.getPaid())) {
            return new LeavePreviewDTO(totalDays, 0, 0);
        }

        EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
        int remainingBefore = balance.getRemainingLeaves();
        int remainingAfter = remainingBefore - totalDays;

        if (remainingAfter < 0) {
            throw new RuntimeException(insufficientBalanceMessage(employee, policy, totalDays));
        }

        return new LeavePreviewDTO(totalDays, remainingBefore, remainingAfter);
    }

    @Transactional
    public LeaveHistoryDTO applyLeave(Long employeeId, LeaveRequestDTO dto, MultipartFile supportingDocument) {
        validateDates(dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());
        validateReturnDate(dto.getEndDate(), dto.getReturnDate());

        // A leave can't be requested for a date that has already passed.
        if (dto.getStartDate() != null && dto.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Leave cannot be requested for a past date");
        }

        Employee employee = getEmployee(employeeId);
        validateEmployeeOwnership(employeeId, employee);

        // Realtime overlap guard: an employee can't hold two leaves over the
        // same dates. They may still book a future, non-overlapping leave even
        // while currently on leave.
        validateNoOverlappingLeave(employeeId, null, dto.getStartDate(), dto.getEndDate());

        boolean includeWeekends = Boolean.TRUE.equals(dto.getIncludeWeekends());
        int totalDays = calculateDays(dto.getStartDate(), dto.getEndDate(), includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, dto.getLeaveType());
        validateGender(policy, employee);
        validateReligion(policy, employee);
        enforceAccrualRules(employee, policy);
        validateSupportingDocument(policy.getLeaveType(), supportingDocument);

        if (Boolean.TRUE.equals(policy.getPaid())) {
            EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
            if (balance.getRemainingLeaves() < totalDays) {
                throw new RuntimeException(insufficientBalanceMessage(employee, policy, totalDays));
            }
        }

        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(employee);
        leave.setLeaveCode(documentSequenceService.generateNext("LV"));
        leave.setLeaveType(policy.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setDateReported(LocalDate.now());
        leave.setReturnDate(dto.getReturnDate());
        leave.setTotalDays(totalDays);
        leave.setIncludeWeekends(includeWeekends);
        leave.setLeaveStatus(LeaveStatus.PENDING);
        leave.setDelegate(resolveDelegate(employee, dto.getDelegateId()));

        leave = leaveRepo.save(leave);

        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            FileUploadResult uploadResult = fileStorageService.upload(
                    supportingDocument,
                    FileCategory.LEAVE_SUPPORTING_DOCUMENT,
                    leave.getId().toString(),
                    true,
                    authContext.getCurrentCompanyId()
            );
            leave.setSupportingDocumentPath(uploadResult.getBlobPath());
            leave = leaveRepo.save(leave);
        }

        return mapToHistoryDTO(leave);
    }

    /**
     * Block a new leave that overlaps an existing pending or approved leave for
     * the same employee. Evaluated live at apply time so two leaves can never
     * cover the same dates; non-overlapping future leaves are still allowed.
     */
    private void validateNoOverlappingLeave(
            Long employeeId, Long excludeLeaveId, LocalDate start, LocalDate end) {
        if (overlapsExisting(employeeId, excludeLeaveId, LeaveStatus.APPROVED, start, end)) {
            throw new IllegalArgumentException(
                    "This employee already has an approved leave that overlaps these dates. "
                            + "A leave can't overlap an existing leave period.");
        }
        if (overlapsExisting(employeeId, excludeLeaveId, LeaveStatus.PENDING, start, end)) {
            throw new IllegalArgumentException(
                    "This employee already has a pending leave request overlapping these dates. "
                            + "Wait for it to be decided, or edit that request instead.");
        }
    }

    /**
     * True if another leave of the given status overlaps [start, end]. When
     * editing, {@code excludeLeaveId} is the leave being changed so it doesn't
     * count as overlapping itself; for a new application it is null.
     */
    private boolean overlapsExisting(
            Long employeeId, Long excludeLeaveId, LeaveStatus status, LocalDate start, LocalDate end) {
        return excludeLeaveId == null
                ? leaveRepo.existsLeavesForPayrollPeriod(employeeId, status, start, end)
                : leaveRepo.existsOtherLeaveForPeriod(employeeId, excludeLeaveId, status, start, end);
    }

    public List<LeaveHistoryDTO> history(Long employeeId) {
        return leaveRepo.findByEmployeeIdOrderByDateReportedDesc(employeeId)
                .stream()
                .map(this::mapToHistoryDTO)
                .toList();
    }

    /**
     * Whether the current authenticated user qualifies as a leave approver.
     * Mirrors {@link #canActAsApprover(Employee)} but returns false instead of
     * throwing for callers that just want to know (e.g. UI gating).
     */
    public boolean canCurrentUserApproveLeaves() {
        try {
            Employee approver = getCurrentEmployee();
            return canActAsApprover(approver);
        } catch (RuntimeException ex) {
            // No employee record, no auth, etc — treat as "no, can't approve".
            return false;
        }
    }

    public List<LeaveHistoryDTO> getPendingApprovalsForCurrentApprover() {
        Employee approver = getCurrentEmployee();

        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to view pending leave approvals");
        }

        Long approverCompanyId = approver.getCompany() != null ? approver.getCompany().getId() : null;
        if (approverCompanyId == null) {
            throw new AccessDeniedException("Approver is not linked to a company");
        }

        return leaveRepo.findByEmployeeCompany_IdAndLeaveStatusOrderByDateReportedDesc(
                        approverCompanyId, LeaveStatus.PENDING)
                .stream()
                .filter(leave -> leave.getEmployee() != null)
                .filter(leave -> leave.getEmployee().getId() != null)
                .filter(leave -> approver.getId() != null && !approver.getId().equals(leave.getEmployee().getId()))
                .filter(leave -> canApproveLeave(approver, leave))
                .map(this::mapToHistoryDTO)
                .toList();
    }

    /**
     * Company-wide history of decided leaves (approved, completed, rejected) for
     * the HR Reports "Leave Approvals" view. Scoped to the approver's company and
     * gated the same way as pending approvals.
     */
    public List<LeaveHistoryDTO> getCompanyLeaveApprovals(boolean archived) {
        Employee approver = getCurrentEmployee();

        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to view leave approvals");
        }

        Long approverCompanyId = approver.getCompany() != null ? approver.getCompany().getId() : null;
        if (approverCompanyId == null) {
            throw new AccessDeniedException("Approver is not linked to a company");
        }

        return leaveRepo.findByCompanyAndStatuses(
                        approverCompanyId,
                        List.of(LeaveStatus.APPROVED, LeaveStatus.COMPLETED, LeaveStatus.REJECTED))
                .stream()
                .filter(leave -> leave.isArchived() == archived)
                .map(this::mapToHistoryDTO)
                .toList();
    }

    /** Archive / unarchive a decided leave so it drops from (or returns to) the active list. */
    @Transactional
    public LeaveHistoryDTO setLeaveArchived(Long leaveId, boolean archived) {
        Employee approver = getCurrentEmployee();
        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to archive leave records");
        }

        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (leave.getEmployee() == null || leave.getEmployee().getCompany() == null
                || approver.getCompany() == null
                || !leave.getEmployee().getCompany().getId().equals(approver.getCompany().getId())) {
            throw new AccessDeniedException("Leave request not found");
        }

        if (leave.getLeaveStatus() == LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Pending leave requests cannot be archived");
        }

        leave.setArchived(archived);
        leave = leaveRepo.save(leave);
        return mapToHistoryDTO(leave);
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

        if (leave.getLeaveStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending leave requests can be approved");
        }

        if (isSelfApproval(approver, leave)) {
            throw new AccessDeniedException("You cannot approve your own leave request");
        }

        if (!canApproveLeave(approver, leave)) {
            throw new AccessDeniedException(
                    "Access denied: only HR manager or the employee's department manager can approve this leave"
            );
        }

        CompanyLeavePolicy policy = getPolicy(leave.getEmployee(), leave.getLeaveType());
        enforceAccrualRules(leave.getEmployee(), policy);

        if (Boolean.TRUE.equals(policy.getPaid())) {
            // Do NOT deduct here. The balance is consumed only when the employee
            // confirms their return (see confirmReturn) because the actual days
            // taken can differ from the planned span — an employee may come back
            // early or have their return delayed. We still verify the planned
            // span fits the current balance so an unaffordable leave isn't approved.
            EmployeeLeaveBalance balance = getOrCreateBalance(leave.getEmployee(), policy);
            if (balance.getRemainingLeaves() < leave.getTotalDays()) {
                throw new RuntimeException(
                        insufficientBalanceMessage(leave.getEmployee(), policy, leave.getTotalDays()));
            }
        }

        leave.setLeaveStatus(LeaveStatus.APPROVED);
        leave = leaveRepo.save(leave);

        // Reflect an in-progress leave on the employee's status so the company
        // can see who is currently away. Only flip an ACTIVE employee — never
        // override INACTIVE / resigned / terminated states. The daily
        // reconciliation job (EmployeeLeaveStatusJob) reverts this to ACTIVE
        // when the leave ends, and flips a future leave to ON_LEAVE when it
        // begins.
        Employee leaveEmployee = leave.getEmployee();
        LocalDate today = LocalDate.now();
        if (leaveEmployee.getStatus() == EmployeeStatus.ACTIVE
                && leave.getStartDate() != null
                && leave.getEndDate() != null
                && !leave.getStartDate().isAfter(today)
                && !leave.getEndDate().isBefore(today)) {
            leaveEmployee.setStatus(EmployeeStatus.ON_LEAVE);
            employeeRepo.save(leaveEmployee);
        }

        return mapToHistoryDTO(leave);
    }

    @Transactional
    public LeaveHistoryDTO rejectLeave(Long leaveId, String rejectionComment) {
        Employee approver = getCurrentEmployee();

        if (!canActAsApprover(approver)) {
            throw new AccessDeniedException("Access denied: no permission to reject leave");
        }

        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (leave.getEmployee() == null) {
            throw new RuntimeException("Leave request employee not found");
        }

        if (leave.getLeaveStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending leave requests can be rejected");
        }

        if (isSelfApproval(approver, leave)) {
            throw new AccessDeniedException("You cannot reject your own leave request — cancel it instead");
        }

        if (!canApproveLeave(approver, leave)) {
            throw new AccessDeniedException(
                    "Access denied: only HR manager or the employee's department manager can reject this leave"
            );
        }

        leave.setLeaveStatus(LeaveStatus.REJECTED);
        leave.setRejectionComment(
                rejectionComment != null && !rejectionComment.isBlank()
                        ? rejectionComment.trim()
                        : null);
        leave = leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    /**
     * Confirms an employee's return to office for an approved leave. This is the
     * point at which the balance is actually consumed: the days deducted are the
     * real days taken (start date up to the day before they resumed duties), so
     * an early or delayed return is reflected accurately. The leave moves to
     * COMPLETED and the employee's status returns to ACTIVE.
     *
     * Permitted for the leave's owner or an HR / department approver.
     */
    @Transactional
    public LeaveHistoryDTO confirmReturn(Long employeeId, Long leaveId, LocalDate reportedDate) {
        if (reportedDate == null) {
            throw new IllegalArgumentException("Reported-to-office date is required");
        }

        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        if (leave.getEmployee() == null || leave.getEmployee().getId() == null) {
            throw new RuntimeException("Leave employee not found");
        }
        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("Leave does not belong to this employee");
        }

        Employee currentEmployee = getCurrentEmployee();
        boolean isOwner = currentEmployee.getId() != null
                && currentEmployee.getId().equals(employeeId);
        if (!isOwner && !canApproveLeave(currentEmployee, leave)) {
            throw new AccessDeniedException(
                    "Only the employee or an HR / department approver can confirm a return");
        }

        if (leave.getLeaveStatus() != LeaveStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved leaves can be marked as returned");
        }

        if (leave.getStartDate() == null || !reportedDate.isAfter(leave.getStartDate())) {
            throw new IllegalArgumentException(
                    "Reported-to-office date must be after the leave start date");
        }

        // Actual leave taken runs from the start date through the day before the
        // employee resumed duties — supports both early and delayed returns.
        boolean includeWeekends = Boolean.TRUE.equals(leave.getIncludeWeekends());
        int actualDays = calculateDays(
                leave.getStartDate(), reportedDate.minusDays(1), includeWeekends);

        Employee employee = leave.getEmployee();
        CompanyLeavePolicy policy = getPolicy(employee, leave.getLeaveType());

        if (Boolean.TRUE.equals(policy.getPaid()) && actualDays > 0) {
            EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
            balance.setRemainingLeaves(balance.getRemainingLeaves() - actualDays);
            try {
                // saveAndFlush surfaces the @Version check synchronously so a
                // concurrent change becomes a clear retry error, not a 500.
                balanceRepo.saveAndFlush(balance);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                throw new IllegalStateException(
                        "Leave balance was modified concurrently — please retry", ex);
            }
        }

        leave.setReturnDate(reportedDate);
        leave.setTotalDays(actualDays);
        leave.setLeaveStatus(LeaveStatus.COMPLETED);
        leave = leaveRepo.save(leave);

        // The employee is back — flip them off ON_LEAVE.
        if (employee.getStatus() == EmployeeStatus.ON_LEAVE) {
            employee.setStatus(EmployeeStatus.ACTIVE);
            employeeRepo.save(employee);
        }

        return mapToHistoryDTO(leave);
    }

    @Transactional
    public LeaveHistoryDTO cancelLeave(Long employeeId, Long leaveId) {
        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        if (leave.getEmployee() == null || leave.getEmployee().getId() == null) {
            throw new RuntimeException("Leave employee not found");
        }

        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only cancel your own leave");
        }

        if (leave.getLeaveStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending leaves can be cancelled");
        }

        leave.setLeaveStatus(LeaveStatus.CANCELLED);
        leave = leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    @Transactional
    public LeaveHistoryDTO updateLeave(Long employeeId, Long leaveId, LeaveRequestDTO dto) {
        return updateLeave(employeeId, leaveId, dto, null);
    }

    @Transactional
    public LeaveHistoryDTO updateLeave(
            Long employeeId,
            Long leaveId,
            LeaveRequestDTO dto,
            MultipartFile supportingDocument) {
        validateDates(dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());
        validateReturnDate(dto.getEndDate(), dto.getReturnDate());

        EmployeeLeave leave = leaveRepo.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));

        if (leave.getEmployee() == null || leave.getEmployee().getId() == null) {
            throw new RuntimeException("Leave employee not found");
        }

        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only update your own leave");
        }

        if (leave.getLeaveStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending leaves can be updated");
        }

        // Editing must not move this leave onto dates already covered by another
        // of the employee's leaves (this leave itself is excluded).
        validateNoOverlappingLeave(employeeId, leaveId, dto.getStartDate(), dto.getEndDate());

        Employee employee = leave.getEmployee();
        boolean includeWeekends = Boolean.TRUE.equals(dto.getIncludeWeekends());
        int newDays = calculateDays(dto.getStartDate(), dto.getEndDate(), includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, dto.getLeaveType());
        validateGender(policy, employee);
        validateReligion(policy, employee);
        enforceAccrualRules(employee, policy);
        validateSupportingDocument(policy.getLeaveType(), supportingDocument);

        if (Boolean.TRUE.equals(policy.getPaid())) {
            EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
            if (balance.getRemainingLeaves() < newDays) {
                throw new RuntimeException(insufficientBalanceMessage(employee, policy, newDays));
            }
        }

        leave.setLeaveType(policy.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setReturnDate(dto.getReturnDate());
        leave.setTotalDays(newDays);
        leave.setIncludeWeekends(includeWeekends);
        leave.setDelegate(resolveDelegate(employee, dto.getDelegateId()));

        leave = leaveRepo.save(leave);

        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            FileUploadResult uploadResult = fileStorageService.upload(
                    supportingDocument,
                    FileCategory.LEAVE_SUPPORTING_DOCUMENT,
                    leave.getId().toString(),
                    true,
                    authContext.getCurrentCompanyId()
            );
            leave.setSupportingDocumentPath(uploadResult.getBlobPath());
            leave = leaveRepo.save(leave);
        }

        return mapToHistoryDTO(leave);
    }

    private Employee getEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    /**
     * Resolves and validates the optional leave delegate. The delegate must be a
     * different employee in the same department as the requestor. Returns null
     * when no delegate is supplied (delegation is optional).
     */
    private Employee resolveDelegate(Employee requestor, Long delegateId) {
        if (delegateId == null) {
            return null;
        }

        Employee delegate = employeeRepo.findById(delegateId)
                .orElseThrow(() -> new RuntimeException("Selected delegate not found"));

        if (requestor.getId() != null && requestor.getId().equals(delegate.getId())) {
            throw new IllegalArgumentException("You cannot delegate to yourself");
        }

        Long requestorDept = requestor.getDepartment() != null ? requestor.getDepartment().getId() : null;
        Long delegateDept = delegate.getDepartment() != null ? delegate.getDepartment().getId() : null;

        if (requestorDept == null || delegateDept == null || !requestorDept.equals(delegateDept)) {
            throw new IllegalArgumentException("Delegate must be a colleague from the same department");
        }

        return delegate;
    }

    private Employee getCurrentEmployee() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        User currentUser = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Employee emp = authContext.getCurrentEmployee();
        if (emp != null) return emp;

        return employeeRepo.findByUser_Id(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Employee record not found"));
    }

    private void validateEmployeeOwnership(Long employeeId, Employee employee) {
        if (employee == null || employee.getId() == null || !employee.getId().equals(employeeId)) {
            throw new AccessDeniedException("You can apply leave only for your own employee record");
        }
    }

    private boolean canActAsApprover(Employee approver) {
        return isHrApprover(approver) || isDepartmentManager(approver);
    }

    private boolean canApproveLeave(Employee approver, EmployeeLeave leave) {
        if (leave == null || leave.getEmployee() == null) {
            return false;
        }

        // Defense in depth — even if a future caller skips the explicit
        // self-approval check, fall through to deny.
        if (isSelfApproval(approver, leave)) {
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

    private boolean isSelfApproval(Employee approver, EmployeeLeave leave) {
        return approver != null
                && approver.getId() != null
                && leave != null
                && leave.getEmployee() != null
                && approver.getId().equals(leave.getEmployee().getId());
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

    /**
     * The return-to-office date is optional, but when provided it must fall
     * after the leave end date — the employee can only head back once the
     * leave has finished.
     */
    private void validateReturnDate(LocalDate end, LocalDate returnDate) {
        if (returnDate == null) {
            return;
        }
        if (end != null && !returnDate.isAfter(end)) {
            throw new IllegalArgumentException(
                    "Reporting-to-office date must be after the leave end date");
        }
    }

    private CompanyLeavePolicy getPolicy(Employee employee, String leaveType) {
        String role = getLeaveRole(employee);

        if (role == null) {
            throw new RuntimeException("Employee company role not configured");
        }

        return policyRepo.findByCompanyOrderByIdDesc(employee.getCompany())
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

    private void validateReligion(CompanyLeavePolicy policy, Employee employee) {
        if (!Boolean.TRUE.equals(policy.getReligionRestricted())) {
            return;
        }

        String employeeReligion = clean(employee.getReligion());

        if (employeeReligion == null || !same(employeeReligion, policy.getAllowedReligion())) {
            throw new RuntimeException(
                    "This leave type is restricted to " + policy.getAllowedReligion() + " employees");
        }
    }

    private EmployeeLeaveBalance getOrCreateBalance(Employee employee, CompanyLeavePolicy policy) {
        EmployeeLeaveBalance balance = findBalance(employee, policy.getLeaveType())
                .orElseGet(() -> {
                    EmployeeLeaveBalance newBalance = new EmployeeLeaveBalance();
                    newBalance.setEmployee(employee);
                    newBalance.setLeaveType(balanceKey(policy.getLeaveType()));
                    int initial = isAccruedAnnualLeave(employee, policy)
                            ? accruedAnnualDays(employee, LocalDate.now())
                            : policy.getDefaultDays();
                    newBalance.setTotalLeaves(initial);
                    newBalance.setRemainingLeaves(initial);
                    return balanceRepo.save(newBalance);
                });

        // For accrued annual leave, the "total" must follow months worked rather
        // than the static policy default. Recompute on every read so balances
        // reflect the live accrual without needing a nightly job.
        if (isAccruedAnnualLeave(employee, policy)) {
            int accrued = accruedAnnualDays(employee, LocalDate.now());
            int used = Math.max(balance.getTotalLeaves() - balance.getRemainingLeaves(), 0);
            int newRemaining = Math.max(accrued - used, 0);
            if (balance.getTotalLeaves() != accrued || balance.getRemainingLeaves() != newRemaining) {
                balance.setTotalLeaves(accrued);
                balance.setRemainingLeaves(newRemaining);
                balance = balanceRepo.save(balance);
            }
        }

        return balance;
    }

    /**
     * Enforces the configurable annual-leave rules (minimum service period and
     * accrual cap). Other leave types are unaffected.
     */
    private void enforceAccrualRules(Employee employee, CompanyLeavePolicy policy) {
        if (!isAccruedAnnualLeave(employee, policy)) {
            return;
        }

        Company company = employee.getCompany();
        Integer minMonths = company.getMinServiceMonthsForAnnualLeave();
        if (minMonths == null || minMonths <= 0) {
            return;
        }

        LocalDate joinDate = resolveJoinDate(employee);
        if (joinDate == null) {
            throw new RuntimeException(
                    "Annual leave requires a join date — set it on the employee profile "
                            + "or the Current Job's start / effective date");
        }

        long monthsWorked = ChronoUnit.MONTHS.between(joinDate, LocalDate.now());
        if (monthsWorked < minMonths) {
            throw new RuntimeException(
                    "Annual leave requires a minimum of " + minMonths
                            + " months of service (current: " + monthsWorked + ")");
        }
    }

    /**
     * Effective join date for accrual purposes. Prefers
     * {@link Employee#getJoinDate()}; if HR only filled in the Current Job
     * dates, falls back to the current-job start date (and then to
     * effective-from). Returning null tells the caller no date is available.
     */
    private LocalDate resolveJoinDate(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (employee.getJoinDate() != null) {
            return employee.getJoinDate();
        }
        if (employee.getId() == null) {
            return null;
        }
        return currentJobRepo.findByEmployee_Id(employee.getId())
                .map(job -> job.getStartDate() != null
                        ? job.getStartDate()
                        : job.getEffectiveFrom())
                .orElse(null);
    }

    private boolean isAccruedAnnualLeave(Employee employee, CompanyLeavePolicy policy) {
        if (employee == null || employee.getCompany() == null || policy == null) {
            return false;
        }
        return employee.getCompany().isAnnualLeaveAccrualEnabled()
                && isAnnualLeave(policy.getLeaveType());
    }

    private boolean isAnnualLeave(String leaveType) {
        String normalized = key(leaveType);
        return normalized != null && normalized.contains("ANNUAL");
    }

    /** Average days per month — used to prorate accrual within a partial month. */
    private static final BigDecimal AVG_DAYS_PER_MONTH = new BigDecimal("30.4375");

    /**
     * Builds a clearer "insufficient balance" message. For accrued annual leave,
     * shows current balance, days requested, days of service, and accrual rate
     * so HR can see *why* there isn't enough yet rather than guessing.
     */
    private String insufficientBalanceMessage(Employee employee, CompanyLeavePolicy policy, int requestedDays) {
        EmployeeLeaveBalance balance = getOrCreateBalance(employee, policy);
        int remaining = balance.getRemainingLeaves();

        if (isAccruedAnnualLeave(employee, policy)) {
            LocalDate joinDate = resolveJoinDate(employee);
            long daysWorked = joinDate != null
                    ? Math.max(ChronoUnit.DAYS.between(joinDate, LocalDate.now()), 0)
                    : 0;
            BigDecimal rate = employee.getCompany().getAnnualLeaveAccrualDaysPerMonth();
            return "Insufficient leave balance: requested " + requestedDays
                    + " day(s) but only " + remaining + " accrued (employee has "
                    + daysWorked + " day(s) of service at " + rate
                    + " day(s) per month).";
        }

        return "Insufficient leave balance: requested " + requestedDays
                + " day(s) but only " + remaining + " available.";
    }

    private int accruedAnnualDays(Employee employee, LocalDate asOf) {
        LocalDate joinDate = resolveJoinDate(employee);
        if (joinDate == null) {
            return 0;
        }
        long daysWorked = ChronoUnit.DAYS.between(joinDate, asOf);
        if (daysWorked <= 0) {
            return 0;
        }
        BigDecimal rate = employee.getCompany().getAnnualLeaveAccrualDaysPerMonth();
        if (rate == null) {
            rate = new BigDecimal("1.50");
        }
        // Day-prorated accrual: a new hire who's been here 29 days earns
        // floor(29 * 1.5 / 30.4375) = 1 day rather than getting nothing
        // until day 30.
        BigDecimal months = BigDecimal.valueOf(daysWorked)
                .divide(AVG_DAYS_PER_MONTH, 6, RoundingMode.HALF_UP);
        return rate.multiply(months)
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
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
        dto.setReturnDate(leave.getReturnDate());
        dto.setTotalDays(leave.getTotalDays());
        dto.setIncludeWeekends(Boolean.TRUE.equals(leave.getIncludeWeekends()));
        dto.setSupportingDocumentUrl(
                leave.getSupportingDocumentPath() != null
                        ? fileStorageService.getPublicUrl(leave.getSupportingDocumentPath())
                        : null
        );
        dto.setLeaveStatus(leave.getLeaveStatus() != null ? leave.getLeaveStatus().name() : null);
        dto.setRejectionComment(leave.getRejectionComment());
        dto.setArchived(leave.isArchived());
        if (leave.getDelegate() != null) {
            dto.setDelegateId(leave.getDelegate().getId());
            dto.setDelegateName(
                    (safeName(leave.getDelegate().getFirstName()) + " "
                            + safeName(leave.getDelegate().getLastName())).trim());
        }
        return dto;
    }

    private String safeName(String value) {
        return value == null ? "" : value;
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
