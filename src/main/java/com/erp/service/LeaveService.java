package com.erp.service;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.domain.LeaveStatus;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
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

        Employee employee = getEmployee(employeeId);
        validateEmployeeOwnership(employeeId, employee);

        boolean includeWeekends = Boolean.TRUE.equals(dto.getIncludeWeekends());
        int totalDays = calculateDays(dto.getStartDate(), dto.getEndDate(), includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, dto.getLeaveType());
        validateGender(policy, employee);
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
            EmployeeLeaveBalance balance = getOrCreateBalance(leave.getEmployee(), policy);

            if (balance.getRemainingLeaves() < leave.getTotalDays()) {
                throw new RuntimeException(
                        insufficientBalanceMessage(leave.getEmployee(), policy, leave.getTotalDays()));
            }

            balance.setRemainingLeaves(balance.getRemainingLeaves() - leave.getTotalDays());
            try {
                // saveAndFlush triggers the @Version check synchronously so we
                // can translate concurrent-modification into a clear error
                // rather than a generic commit-time 500.
                balanceRepo.saveAndFlush(balance);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                throw new IllegalStateException(
                        "Leave balance was modified by another approval — please retry", ex);
            }
        }

        leave.setLeaveStatus(LeaveStatus.APPROVED);
        leave = leaveRepo.save(leave);

        return mapToHistoryDTO(leave);
    }

    @Transactional
    public LeaveHistoryDTO rejectLeave(Long leaveId) {
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
        leave = leaveRepo.save(leave);

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

        Employee employee = leave.getEmployee();
        boolean includeWeekends = Boolean.TRUE.equals(dto.getIncludeWeekends());
        int newDays = calculateDays(dto.getStartDate(), dto.getEndDate(), includeWeekends);

        CompanyLeavePolicy policy = getPolicy(employee, dto.getLeaveType());
        validateGender(policy, employee);
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
        leave.setTotalDays(newDays);
        leave.setIncludeWeekends(includeWeekends);
        leave.setDelegate(resolveDelegate(employee, dto.getDelegateId()));

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
        dto.setTotalDays(leave.getTotalDays());
        dto.setIncludeWeekends(Boolean.TRUE.equals(leave.getIncludeWeekends()));
        dto.setSupportingDocumentUrl(
                leave.getSupportingDocumentPath() != null
                        ? fileStorageService.getPublicUrl(leave.getSupportingDocumentPath())
                        : null
        );
        dto.setLeaveStatus(leave.getLeaveStatus() != null ? leave.getLeaveStatus().name() : null);
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
