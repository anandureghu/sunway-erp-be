package com.erp.service.salary;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.salary.Payroll;
import com.erp.dto.salary.PayrollAccountStatusDTO;
import com.erp.dto.salary.PayrollBatchResponseDTO;
import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.dto.salary.PayrollHistoryDTO;
import com.erp.dto.salary.PayrollPreviewDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeTimesheetRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.service.finance.CoaBalanceRules;
import com.erp.service.finance.TransactionService;
import com.erp.service.hr.ProcessAccountDefaultsService;
import com.erp.service.hr.RetirementCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final double STANDARD_HOURS_PER_DAY = 8.0;

    /**
     * Employment-ending statuses. A payroll run for an employee in any of these
     * states is a final settlement: the accrued end-of-service gratuity is paid
     * and any active loans are recovered in full from it. Such employees are
     * excluded from bulk runs (which only process {@link EmployeeStatus#ACTIVE})
     * and must be processed individually.
     */
    private static final Set<EmployeeStatus> FINAL_SETTLEMENT_STATUSES =
            EnumSet.of(EmployeeStatus.TERMINATED, EmployeeStatus.RESIGNED, EmployeeStatus.RETIRED);

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeBankDetailsRepository bankRepo;
    private final EmployeeLoanRepository loanRepo;
    private final EmployeeTimesheetRepository timesheetRepo;
    private final EmployeeLeaveRepository leaveRepo;
    private final CompanyLeavePolicyRepository leavePolicyRepo;
    private final PayrollRepository payrollRepo;
    private final DocumentSequenceService documentSequenceService;
    private final RetirementCompensationService retirementCompensationService;
    private final ProcessAccountDefaultsService processAccountDefaultsService;
    private final TransactionService transactionService;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public PayrollPreviewDTO previewPayroll(Long employeeId, PayrollGenerateRequestDTO dto) {
        validateRequest(dto);

        Employee employee = getEmployee(employeeId);
        EmployeeCompensation compensation = getActiveCompensation(employee);

        PayrollComputation computation = computePayroll(
                employee,
                compensation,
                dto.getPayPeriodStart(),
                dto.getPayPeriodEnd()
        );

        double grossPay = round2(computation.earnedGrossPay() + computation.endOfServiceCompensation());
        PayrollAccountStatusDTO accountStatus = resolvePayrollAccountStatus(
                employee.getCompanyId(), grossPay);

        return toPreviewDTO(computation, grossPay, accountStatus);
    }

    @Transactional(readOnly = true)
    public PayrollAccountStatusDTO getPayrollAccountStatus(Long companyId) {
        assertCallerCompany(companyId);
        return resolvePayrollAccountStatus(companyId, 0.0);
    }

    @Transactional
    public Payroll generatePayroll(Long employeeId, PayrollGenerateRequestDTO dto) {
        validateRequest(dto);

        Employee employee = getEmployee(employeeId);
        EmployeeCompensation compensation = getActiveCompensation(employee);
        EmployeeBankDetails bankDetails = getBankDetails(employee);

        validateDuplicatePayroll(employee, dto.getPayPeriodStart(), dto.getPayPeriodEnd());
        validateNoPendingLeaves(employeeId, dto.getPayPeriodStart(), dto.getPayPeriodEnd());

        PayrollComputation computation = computePayroll(
                employee,
                compensation,
                dto.getPayPeriodStart(),
                dto.getPayPeriodEnd()
        );

        Payroll payroll = buildPayroll(employee, bankDetails, dto, computation);
        Payroll saved = payrollRepo.save(payroll);

        applyLoanRecovery(employee, computation.finalSettlement());
        postPayrollToAccounting(saved, employee);

        return saved;
    }

    @Transactional
    public PayrollBatchResponseDTO generatePayrollBatch(Long companyId, PayrollGenerateRequestDTO dto) {
        assertCallerCompany(companyId);
        validateRequest(dto);

        List<Employee> employees = getActiveEmployeesByCompany(companyId);

        int generatedCount = 0;

        for (Employee employee : employees) {
            EmployeeCompensation compensation = getActiveCompensation(employee);
            EmployeeBankDetails bankDetails = getBankDetails(employee);

            validateDuplicatePayroll(employee, dto.getPayPeriodStart(), dto.getPayPeriodEnd());
            validateNoPendingLeaves(employee.getId(), dto.getPayPeriodStart(), dto.getPayPeriodEnd());

            PayrollComputation computation = computePayroll(
                    employee,
                    compensation,
                    dto.getPayPeriodStart(),
                    dto.getPayPeriodEnd()
            );

            Payroll payroll = buildPayroll(employee, bankDetails, dto, computation);
            Payroll saved = payrollRepo.save(payroll);

            applyLoanRecovery(employee, computation.finalSettlement());
            postPayrollToAccounting(saved, employee);
            generatedCount++;
        }

        String payrollMonth = dto.getPayDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        return PayrollBatchResponseDTO.builder()
                .generatedCount(generatedCount)
                .payrollMonth(payrollMonth)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PayrollHistoryDTO> getPayrollHistory(Long employeeId) {
        Employee employee = getEmployee(employeeId);

        return payrollRepo.findByEmployeeOrderByPayDateDesc(employee)
                .stream()
                .map(this::toHistoryDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PayrollHistoryDTO getLatestPayrollForMonth(Long employeeId, LocalDate start, LocalDate end) {
        Employee employee = getEmployee(employeeId);

        Payroll payroll = payrollRepo
                .findTopByEmployeeAndPayDateBetweenOrderByPayDateDesc(employee, start, end)
                .orElseThrow(() -> new RuntimeException("No payroll found for selected month"));

        return toHistoryDTO(payroll);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectedPayrollAmounts> computeProjectedAmounts(Employee employee) {
        EmployeeCompensation compensation = getActiveCompensation(employee);

        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.withDayOfMonth(1);
        LocalDate periodEnd = today.withDayOfMonth(today.lengthOfMonth());

        PayrollComputation computation = computePayroll(
                employee,
                compensation,
                periodStart,
                periodEnd
        );

        return Optional.of(new ProjectedPayrollAmounts(
                computation.earnedGrossPay(),
                computation.totalDeductions(),
                computation.netPayable()
        ));
    }

    private Payroll buildPayroll(
            Employee employee,
            EmployeeBankDetails bankDetails,
            PayrollGenerateRequestDTO dto,
            PayrollComputation computation
    ) {
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setCompany(employee.getCompany());
        payroll.setPayrollCode(generatePayrollCode(employee.getId()));
        payroll.setPayPeriodStart(dto.getPayPeriodStart());
        payroll.setPayPeriodEnd(dto.getPayPeriodEnd());
        payroll.setPayDate(dto.getPayDate());

        // Gross includes the end-of-service gratuity so it reconciles with net on a final run.
        payroll.setGrossPay(round2(computation.earnedGrossPay() + computation.endOfServiceCompensation()));
        payroll.setEndOfServiceCompensation(computation.endOfServiceCompensation());
        payroll.setFinalSettlement(computation.finalSettlement());
        payroll.setLoanDeduction(computation.loanDeduction());
        payroll.setDeductions(computation.totalDeductions());
        payroll.setNetPayable(computation.netPayable());

        payroll.setWorkedHours(computation.workedHours());
        payroll.setWorkedDays(computation.workedDays());
        payroll.setPaidLeaveDays(computation.paidLeaveDays());
        payroll.setUnpaidLeaveDays(computation.unpaidLeaveDays());
        payroll.setPayableDays(computation.payableDays());
        payroll.setLopDays(computation.lopDays());
        payroll.setLopAmount(computation.lopAmount());

        payroll.setBankName(bankDetails.getBankName());
        payroll.setBankAccount(bankDetails.getAccountNo());

        return payroll;
    }

    private void postPayrollToAccounting(Payroll payroll, Employee employee) {
        Long companyId = employee.getCompanyId();
        if (companyId == null) {
            throw new RuntimeException("Employee has no company");
        }

        Long debitAccountId = processAccountDefaultsService
                .resolveProcessDebitAccount(companyId, AccountingProcessCode.PAYROLL)
                .orElseThrow(() -> new RuntimeException(
                        "Configure payroll debit account under Finance → Default accounts → Process account defaults"));

        Long creditAccountId = processAccountDefaultsService
                .resolveProcessCreditAccount(companyId, AccountingProcessCode.PAYROLL)
                .orElseThrow(() -> new RuntimeException(
                        "Configure payroll credit account under Finance → Default accounts → Process account defaults"));

        BigDecimal amount = BigDecimal.valueOf(payroll.getGrossPay());
        String employeeLabel = employee.getEmployeeNo() != null && !employee.getEmployeeNo().isBlank()
                ? employee.getEmployeeNo()
                : String.valueOf(employee.getId());
        String desc = "Payroll " + payroll.getPayrollCode() + " — " + employeeLabel;

        transactionService.recordPayrollPosting(
                companyId,
                payroll.getId(),
                amount,
                debitAccountId,
                creditAccountId,
                desc);
    }

    private List<Employee> getActiveEmployeesByCompany(Long companyId) {
        List<Employee> employees = employeeRepo.findByCompany_IdAndStatus(companyId, EmployeeStatus.ACTIVE);

        if (employees == null || employees.isEmpty()) {
            throw new RuntimeException("No active employees found for company id: " + companyId);
        }

        return employees;
    }

    private PayrollComputation computePayroll(
            Employee employee,
            EmployeeCompensation compensation,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        double monthlyGross = safe(compensation.getTotalCompensation());

        if (monthlyGross <= 0) {
            throw new RuntimeException("Employee total compensation must be greater than zero");
        }

        int workingDays = countWorkingDays(periodStart, periodEnd);
        if (workingDays <= 0) {
            throw new RuntimeException("No working days found in selected payroll period");
        }

        double perDaySalary = monthlyGross / workingDays;

        List<EmployeeTimesheet> timesheets = timesheetRepo.findByEmployeeIdAndAttendanceDateBetween(
                employee.getId(),
                periodStart,
                periodEnd
        );

        double workedHours = timesheets.stream()
                .mapToLong(this::resolveWorkedMinutes)
                .sum() / 60.0;

        double workedDays = workedHours / STANDARD_HOURS_PER_DAY;

        List<EmployeeLeave> approvedLeaves = leaveRepo.findApprovedLeavesForPayrollPeriod(
                employee.getId(),
                periodStart,
                periodEnd
        );

        double paidLeaveDays = 0.0;
        double unpaidLeaveDays = 0.0;

        for (EmployeeLeave leave : approvedLeaves) {
            double leaveDays = calculateLeaveDaysWithinPeriod(leave, periodStart, periodEnd);
            if (leaveDays <= 0) {
                continue;
            }

            if (isPaidLeave(employee, leave.getLeaveType())) {
                paidLeaveDays += leaveDays;
            } else {
                unpaidLeaveDays += leaveDays;
            }
        }

        double payableDays = Math.min(workedDays + paidLeaveDays, workingDays);
        double lopDays = Math.max(workingDays - payableDays, 0.0);
        double lopAmount = lopDays * perDaySalary;
        double earnedGrossPay = Math.max(monthlyGross - lopAmount, 0.0);

        boolean finalSettlement = isFinalSettlement(employee);

        // End-of-service gratuity is paid only on a final settlement (exiting employee).
        double endOfServiceCompensation = finalSettlement
                ? retirementCompensationService.computeAccruedAmount(employee).doubleValue()
                : 0.0;

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        // Normal runs recover the monthly installment; a final settlement clears the
        // full outstanding balance, deducted from the end-of-service payment.
        double loanDeduction = activeLoans.stream()
                .mapToDouble(loan -> finalSettlement
                        ? Math.max(safe(loan.getBalance()), 0.0)
                        : Math.min(safe(loan.getMonthlyDeduction()), Math.max(safe(loan.getBalance()), 0.0)))
                .sum();

        double totalDeductions = loanDeduction;
        double netPayable = (earnedGrossPay + endOfServiceCompensation) - totalDeductions;

        if (netPayable < 0) {
            netPayable = 0.0;
        }

        return new PayrollComputation(
                round2(monthlyGross),
                workingDays,
                round2(perDaySalary),
                round2(workedHours),
                round2(workedDays),
                round2(paidLeaveDays),
                round2(unpaidLeaveDays),
                round2(payableDays),
                round2(lopDays),
                round2(lopAmount),
                round2(loanDeduction),
                round2(totalDeductions),
                round2(netPayable),
                round2(earnedGrossPay),
                round2(endOfServiceCompensation),
                finalSettlement
        );
    }

    private boolean isFinalSettlement(Employee employee) {
        return employee != null && FINAL_SETTLEMENT_STATUSES.contains(employee.getStatus());
    }

    private double calculateLeaveDaysWithinPeriod(
            EmployeeLeave leave,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        LocalDate effectiveStart = leave.getStartDate().isBefore(periodStart)
                ? periodStart
                : leave.getStartDate();

        LocalDate effectiveEnd = leave.getEndDate().isAfter(periodEnd)
                ? periodEnd
                : leave.getEndDate();

        if (effectiveEnd.isBefore(effectiveStart)) {
            return 0.0;
        }

        boolean includeWeekends = Boolean.TRUE.equals(leave.getIncludeWeekends());
        return calculateDays(effectiveStart, effectiveEnd, includeWeekends);
    }

    private int calculateDays(LocalDate start, LocalDate end, boolean includeWeekends) {
        int days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (includeWeekends || !isWeekend(date)) {
                days++;
            }
        }
        return days;
    }

    private boolean isPaidLeave(Employee employee, String leaveType) {
        String role = getLeaveRole(employee);

        if (employee == null || employee.getCompany() == null || role == null) {
            return false;
        }

        return leavePolicyRepo.findByCompanyOrderByIdDesc(employee.getCompany())
                .stream()
                .filter(policy -> same(policy.getRole(), role))
                .filter(policy -> same(policy.getLeaveType(), leaveType))
                .findFirst()
                .map(CompanyLeavePolicy::getPaid)
                .orElse(false);
    }

    private void validateNoPendingLeaves(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        boolean hasPendingLeaves = leaveRepo.existsPendingLeavesForPayrollPeriod(
                employeeId,
                periodStart,
                periodEnd
        );

        if (hasPendingLeaves) {
            throw new RuntimeException("Cannot generate payroll while leave approvals are pending for this period");
        }
    }

    private void validateDuplicatePayroll(Employee employee, LocalDate payPeriodStart, LocalDate payPeriodEnd) {
        boolean exists = payrollRepo.existsByEmployeeAndPayPeriodStartAndPayPeriodEnd(
                employee,
                payPeriodStart,
                payPeriodEnd
        );

        if (exists) {
            throw new RuntimeException("Payroll already generated for employee: " + employee.getEmployeeNo());
        }
    }

    private void applyLoanRecovery(Employee employee, boolean finalSettlement) {
        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        for (EmployeeLoan loan : activeLoans) {
            double monthlyDeduction = safe(loan.getMonthlyDeduction());
            double balance = safe(loan.getBalance());

            // A final settlement clears the whole balance; otherwise recover one installment.
            double actualRecovery = finalSettlement ? balance : Math.min(monthlyDeduction, balance);
            loan.setBalance(round2(Math.max(balance - actualRecovery, 0.0)));

            if (loan.getBalance() <= 0.0) {
                loan.setStatus(STATUS_CLOSED);
            }

            loanRepo.save(loan);
        }
    }

    private Employee getEmployee(Long employeeId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);
        return employee;
    }

    private void assertSameTenant(Employee employee) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee != null && employee.getCompany() != null
                ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This employee belongs to a different company");
        }
    }

    private void assertCallerCompany(Long companyId) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId == null || companyId == null || !currentCompanyId.equals(companyId)) {
            throw new AccessDeniedException("This company belongs to a different tenant");
        }
    }

    private EmployeeCompensation getActiveCompensation(Employee employee) {
        return compensationRepo.findActiveByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("Active compensation not found for employee"));
    }

    private EmployeeBankDetails getBankDetails(Employee employee) {
        return bankRepo.findByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("Employee bank details not found"));
    }

    private void validateRequest(PayrollGenerateRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Payroll request is required");
        }

        if (dto.getPayPeriodStart() == null || dto.getPayPeriodEnd() == null || dto.getPayDate() == null) {
            throw new IllegalArgumentException("Pay period start, pay period end, and pay date are required");
        }

        if (dto.getPayPeriodEnd().isBefore(dto.getPayPeriodStart())) {
            throw new IllegalArgumentException("Pay period end cannot be before pay period start");
        }

        if (dto.getPayDate().isBefore(dto.getPayPeriodEnd())) {
            throw new IllegalArgumentException("Pay date cannot be before pay period end");
        }
    }

    private long resolveWorkedMinutes(EmployeeTimesheet timesheet) {
        if (timesheet.getWorkedMinutes() != null) {
            return timesheet.getWorkedMinutes();
        }

        if (timesheet.getCheckInTime() != null && timesheet.getCheckOutTime() != null) {
            return Duration.between(timesheet.getCheckInTime(), timesheet.getCheckOutTime()).toMinutes();
        }

        return 0L;
    }

    private int countWorkingDays(LocalDate start, LocalDate end) {
        int days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!isWeekend(date)) {
                days++;
            }
        }
        return days;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private PayrollPreviewDTO toPreviewDTO(
            PayrollComputation computation,
            double grossPay,
            PayrollAccountStatusDTO payrollAccount) {
        return new PayrollPreviewDTO(
                computation.monthlyGross(),
                computation.workingDays(),
                computation.perDaySalary(),
                computation.workedHours(),
                computation.workedDays(),
                computation.paidLeaveDays(),
                computation.unpaidLeaveDays(),
                computation.payableDays(),
                computation.lopDays(),
                computation.lopAmount(),
                computation.loanDeduction(),
                computation.totalDeductions(),
                computation.netPayable(),
                computation.earnedGrossPay(),
                computation.endOfServiceCompensation(),
                computation.finalSettlement(),
                grossPay,
                payrollAccount
        );
    }

    private PayrollAccountStatusDTO resolvePayrollAccountStatus(Long companyId, double grossAmount) {
        if (companyId == null) {
            return PayrollAccountStatusDTO.builder()
                    .status("NOT_CONFIGURED")
                    .configured(false)
                    .availableBalance(0)
                    .payrollGrossAmount(grossAmount)
                    .sufficientFunds(false)
                    .build();
        }

        Optional<Long> debitAccountId = processAccountDefaultsService
                .resolveProcessDebitAccount(companyId, AccountingProcessCode.PAYROLL);
        if (debitAccountId.isEmpty()) {
            return PayrollAccountStatusDTO.builder()
                    .status("NOT_CONFIGURED")
                    .configured(false)
                    .availableBalance(0)
                    .payrollGrossAmount(grossAmount)
                    .sufficientFunds(false)
                    .build();
        }

        ChartOfAccounts account = chartOfAccountsRepository.findById(debitAccountId.get())
                .orElseThrow(() -> new RuntimeException("Payroll debit account not found"));
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        boolean sufficient = grossAmount <= 0;
        if (grossAmount > 0) {
            try {
                CoaBalanceRules.assertSufficientBalance(account, BigDecimal.valueOf(grossAmount).negate());
                sufficient = true;
            } catch (Exception ex) {
                sufficient = false;
            }
        } else {
            sufficient = true;
        }

        return PayrollAccountStatusDTO.builder()
                .status(sufficient ? "READY" : "INSUFFICIENT")
                .configured(true)
                .debitAccountId(account.getId())
                .debitAccountCode(account.getAccountCode())
                .debitAccountName(account.getAccountName())
                .availableBalance(balance.doubleValue())
                .payrollGrossAmount(grossAmount)
                .sufficientFunds(sufficient)
                .build();
    }

    private PayrollHistoryDTO toHistoryDTO(Payroll payroll) {
        PayrollHistoryDTO dto = new PayrollHistoryDTO();
        dto.setPayrollCode(payroll.getPayrollCode());
        dto.setPayPeriodStart(payroll.getPayPeriodStart());
        dto.setPayPeriodEnd(payroll.getPayPeriodEnd());
        dto.setPayDate(payroll.getPayDate());
        dto.setGrossPay(payroll.getGrossPay());
        dto.setLoanDeduction(payroll.getLoanDeduction());
        dto.setTotalDeductions(payroll.getDeductions());
        dto.setNetPayable(payroll.getNetPayable());
        dto.setEndOfServiceCompensation(payroll.getEndOfServiceCompensation());
        dto.setFinalSettlement(payroll.isFinalSettlement());
        return dto;
    }

    private String getLeaveRole(Employee employee) {
        if (employee.getCompanyRole() != null) {
            String companyRole = clean(employee.getCompanyRole());
            if (companyRole != null) {
                return companyRole;
            }
        }
        return clean(employee.getRole());
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

    private boolean same(String left, String right) {
        String leftKey = key(left);
        String rightKey = key(right);
        return leftKey != null && leftKey.equals(rightKey);
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String generatePayrollCode(Long employeeId) {
        return documentSequenceService.generateNext("PAYROLL");
    }

    public record ProjectedPayrollAmounts(
            double grossPay,
            double deductions,
            double netPayable
    ) {
    }

    public record PayrollComputation(
            double monthlyGross,
            int workingDays,
            double perDaySalary,
            double workedHours,
            double workedDays,
            double paidLeaveDays,
            double unpaidLeaveDays,
            double payableDays,
            double lopDays,
            double lopAmount,
            double loanDeduction,
            double totalDeductions,
            double netPayable,
            double earnedGrossPay,
            double endOfServiceCompensation,
            boolean finalSettlement
    ) {
    }
}