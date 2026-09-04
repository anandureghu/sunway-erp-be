package com.erp.service.salary;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.exception.PayrollGenerationException;
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
import com.erp.dto.salary.PayrollSummaryRowDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeLeaveRepository;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeOvertimeOverrideRepository;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class PayrollService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    // Fallback used only when a company has no explicit standard-hours setting.
    private static final double STANDARD_HOURS_PER_DAY = 6.0;
    // Standard paid working days in a month — the divisor for the daily rate when an
    // organisation has no check-in/out data (Gross / 22 × days worked).
    private static final double STANDARD_WORKING_DAYS_PER_MONTH = 22.0;

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
    private final EmployeeOvertimeOverrideRepository overtimeOverrideRepo;
    private final EmployeeLeaveRepository leaveRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final CompanyLeavePolicyRepository leavePolicyRepo;
    private final PayrollRepository payrollRepo;
    private final DocumentSequenceService documentSequenceService;
    private final RetirementCompensationService retirementCompensationService;
    private final ProcessAccountDefaultsService processAccountDefaultsService;
    private final TransactionService transactionService;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuthContext authContext;
    private final PayslipDocumentService payslipDocumentService;
    private final com.erp.service.notification.EmailService emailService;

    @Transactional(readOnly = true)
    public PayrollPreviewDTO previewPayroll(Long employeeId, PayrollGenerateRequestDTO dto) {
        validateRequest(dto);

        Employee employee = getEmployee(employeeId);
        validateExitCutoff(employee, dto, false);
        EmployeeCompensation compensation = getActiveCompensation(employee);

        PayrollComputation computation = computePayroll(
                employee,
                compensation,
                dto.getPayPeriodStart(),
                dto.getPayPeriodEnd()
        );

        double grossPay = round2(computation.grossEarnings() + computation.endOfServiceCompensation()
                + computation.overtimePay());
        // The funds check should reflect what actually posts to the ledger — the earned
        // salary expense (gross minus loss of pay), not the pre-LOP gross.
        double payrollExpense = round2(grossPay - computation.lopAmount());
        PayrollAccountStatusDTO accountStatus = resolvePayrollAccountStatus(
                employee.getCompanyId(), payrollExpense);

        return toPreviewDTO(computation, grossPay, accountStatus);
    }

    @Transactional(readOnly = true)
    public PayrollAccountStatusDTO getPayrollAccountStatus(Long companyId) {
        assertCallerCompany(companyId);
        return resolvePayrollAccountStatus(companyId, 0.0);
    }

    /**
     * Company-wide payroll history for the HR payroll-summary report, optionally
     * bounded by pay date. Rows carry the employee + department so the report can
     * be grouped by department.
     */
    @Transactional(readOnly = true)
    public List<PayrollSummaryRowDTO> getCompanyPayrollSummary(
            Long companyId, LocalDate from, LocalDate to) {
        assertCallerCompany(companyId);
        return payrollRepo.findCompanyPayrollHistory(companyId, from, to).stream()
                .map(this::toSummaryRow)
                .toList();
    }

    private PayrollSummaryRowDTO toSummaryRow(Payroll p) {
        Employee e = p.getEmployee();
        String dept = e != null && e.getDepartment() != null
                ? e.getDepartment().getDepartmentName()
                : null;
        String name = e == null ? null
                : ((safeStr(e.getFirstName()) + " " + safeStr(e.getLastName())).trim());
        // Derive from the invariant fields (net, loss-of-pay, loan) exactly like the
        // Employee Payroll history does, so this report always reconciles and matches
        // that view: total deductions = loss of pay + loan; gross = net + deductions.
        // (Older rows stored gross already reduced by loss of pay with deductions=0, so
        // trusting the raw columns would show a different gross than the payslip.)
        double net = safe(p.getNetPayable());
        double loans = safe(p.getLoanDeduction());
        double lop = safe(p.getLopAmount());
        double totalDeductions = round2(lop + loans);
        double gross = round2(net + totalDeductions);
        return PayrollSummaryRowDTO.builder()
                .employeeId(e != null ? e.getId() : null)
                .employeeNo(e != null ? e.getEmployeeNo() : null)
                .employeeName(name != null && !name.isBlank() ? name : (e != null ? e.getEmployeeNo() : null))
                .department(dept != null && !dept.isBlank() ? dept : "Unassigned")
                .payrollCode(p.getPayrollCode())
                .payPeriodStart(p.getPayPeriodStart())
                .payPeriodEnd(p.getPayPeriodEnd())
                .payDate(p.getPayDate())
                .grossPay(round2(gross))
                .totalDeductions(round2(totalDeductions))
                .loanDeduction(round2(loans))
                .lopAmount(round2(lop))
                .overtimePay(round2(safe(p.getOvertimePay())))
                .endOfServiceCompensation(round2(safe(p.getEndOfServiceCompensation())))
                .netPayable(round2(net))
                .finalSettlement(p.isFinalSettlement())
                .build();
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    @Transactional
    public Payroll generatePayroll(Long employeeId, PayrollGenerateRequestDTO dto) {
        validateRequest(dto);

        Employee employee = getEmployee(employeeId);
        validateExitCutoff(employee, dto, true);
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

        applyLoanRecovery(employee, computation.finalSettlement(),
                computation.loanDeduction());
        postPayrollToAccounting(saved, employee);
        emailPayslip(employee, saved);

        // A final settlement is the employee's last run — once it's processed, mark
        // them INACTIVE so they drop out of payroll/org listings and can be archived.
        if (computation.finalSettlement()) {
            employee.setStatus(EmployeeStatus.INACTIVE);
            employeeRepo.save(employee);
        }

        return saved;
    }

    @Transactional
    public PayrollBatchResponseDTO generatePayrollBatch(Long companyId, PayrollGenerateRequestDTO dto) {
        assertCallerCompany(companyId);
        validateRequest(dto);

        List<Employee> employees = getPayableEmployeesByCompany(companyId);

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

            applyLoanRecovery(employee, computation.finalSettlement(),
                    computation.loanDeduction());
            postPayrollToAccounting(saved, employee);
            emailPayslip(employee, saved);
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
                computation.grossEarnings() + computation.endOfServiceCompensation()
                        + computation.overtimePay(),
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

        // Gross is the full monthly package plus any end-of-service gratuity; loss of pay
        // and loans are carried in `deductions`, so gross − deductions = net.
        payroll.setGrossPay(round2(computation.grossEarnings() + computation.endOfServiceCompensation()
                + computation.overtimePay()));
        payroll.setEndOfServiceCompensation(computation.endOfServiceCompensation());
        payroll.setFinalSettlement(computation.finalSettlement());
        payroll.setLoanDeduction(computation.loanDeduction());
        payroll.setDeductions(computation.totalDeductions());
        payroll.setNetPayable(computation.netPayable());

        payroll.setWorkedHours(computation.workedHours());
        payroll.setOvertimeHours(computation.overtimeHours());
        payroll.setOvertimePay(computation.overtimePay());
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

        // Post the earned salary expense — full gross minus loss of pay. (Loans are
        // recovered separately in applyLoanRecovery.) This is unchanged by moving LOP
        // into the deductions bucket: gross now holds the full package, so subtract LOP.
        double lop = payroll.getLopAmount() != null ? payroll.getLopAmount() : 0.0;
        double eos = payroll.getEndOfServiceCompensation() != null
                ? payroll.getEndOfServiceCompensation() : 0.0;
        String employeeLabel = employee.getEmployeeNo() != null && !employee.getEmployeeNo().isBlank()
                ? employee.getEmployeeNo()
                : String.valueOf(employee.getId());
        String desc = "Payroll " + payroll.getPayrollCode() + " — " + employeeLabel;

        // On a final settlement, the End-of-Service gratuity posts to the company's
        // configured End-of-Service account as its own ledger entry; the rest of the
        // package posts to the payroll account. If no EOS account is configured, the
        // whole amount posts to payroll as before.
        Long eosAccountId = eos > 0
                ? processAccountDefaultsService.resolveEndOfServiceAccountId(companyId)
                : null;

        if (eosAccountId != null) {
            BigDecimal regular = BigDecimal.valueOf(round2(payroll.getGrossPay() - eos - lop));
            transactionService.recordPayrollPosting(
                    companyId, payroll.getId(), regular, debitAccountId, creditAccountId, desc);
            transactionService.recordEndOfServicePosting(
                    companyId, payroll.getId(), BigDecimal.valueOf(round2(eos)),
                    eosAccountId, creditAccountId,
                    "End of service " + payroll.getPayrollCode() + " — " + employeeLabel);
        } else {
            BigDecimal amount = BigDecimal.valueOf(round2(payroll.getGrossPay() - lop));
            transactionService.recordPayrollPosting(
                    companyId, payroll.getId(), amount, debitAccountId, creditAccountId, desc);
        }
    }

    /**
     * Statuses eligible for a bulk payroll run: currently ACTIVE and ON_LEAVE.
     * UNDER_PROBATION joins this set when the probation feature ships — a
     * probationary employee is still paid.
     */
    private static final Set<EmployeeStatus> PAYABLE_STATUSES =
            EnumSet.of(
                    EmployeeStatus.ACTIVE,
                    EmployeeStatus.ON_LEAVE,
                    EmployeeStatus.UNDER_PROBATION);

    private List<Employee> getPayableEmployeesByCompany(Long companyId) {
        List<Employee> employees =
                employeeRepo.findByCompany_IdAndStatusIn(companyId, PAYABLE_STATUSES);

        if (employees == null || employees.isEmpty()) {
            throw new RuntimeException(
                    "No payable (active / on-leave) employees found for company id: " + companyId);
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
            throw new PayrollGenerationException(
                    "The total salary for " + employeeLabel(employee)
                            + " is zero. Set a salary greater than zero before generating payroll.");
        }

        int workingDays = countWorkingDays(periodStart, periodEnd);
        if (workingDays <= 0) {
            throw new PayrollGenerationException(
                    "The selected pay period has no working days. Choose a period that "
                            + "includes at least one working day (Sun–Thu).");
        }

        // Daily rate: an organisation with no check-in/out data prorates against a fixed
        // 22-day standard month (Gross / 22 × days worked); a punch-in organisation uses
        // the actual working days in the period.
        boolean requireCheckIn = companyRequireCheckIn(employee);
        double referenceDays = requireCheckIn
                ? workingDays
                : STANDARD_WORKING_DAYS_PER_MONTH;
        double perDaySalary = monthlyGross / referenceDays;

        // Approved leaves within the period, split into paid vs unpaid. Computed up
        // front because a no-punch organisation derives its worked days from these.
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

        List<EmployeeTimesheet> timesheets = timesheetRepo.findByEmployeeIdAndAttendanceDateBetween(
                employee.getId(),
                periodStart,
                periodEnd
        );

        double stdHoursPerDay = companyStandardHours(employee);
        long stdMinutes = Math.round(stdHoursPerDay * 60.0);
        double workedHours;
        double workedDays;
        if (!requireCheckIn) {
            // Organisation doesn't punch in/out — every working day is paid EXCEPT
            // approved unpaid leave. Worked days = working days minus all leave; paid
            // leave is added back in payableDays below so only unpaid leave reduces pay.
            workedDays = Math.max(workingDays - paidLeaveDays - unpaidLeaveDays, 0.0);
            workedHours = workedDays * stdHoursPerDay;
        } else {
            // Align exactly with the Attendance History report: worked hours = sum of
            // logged time; worked days = days that reached a full standard day (the
            // report's "Days Worked" / daysPresent), not a fractional hours/standard.
            long totalMinutes = timesheets.stream()
                    .mapToLong(this::resolveWorkedMinutes)
                    .sum();
            workedHours = totalMinutes / 60.0;
            workedDays = timesheets.stream()
                    .filter(t -> resolveWorkedMinutes(t) >= stdMinutes)
                    .count();
        }

        // Overtime = hours logged beyond the standard for the whole period. In a
        // no-punch organisation there are no punches to derive it from, so it comes
        // from the manual monthly override HR keyed in the Time Sheets tab (0 if none).
        double overtimeHours;
        if (!requireCheckIn) {
            overtimeHours = overtimeOverrideRepo
                    .findByEmployee_IdAndYearAndMonth(
                            employee.getId(), periodStart.getYear(), periodStart.getMonthValue())
                    .map(o -> Math.max(0.0, o.getOvertimeHours()))
                    .orElse(0.0);
        } else {
            overtimeHours = Math.max(0.0, workedHours - (workingDays * stdHoursPerDay));
        }

        // Overtime is paid ON TOP of the monthly package: hourly rate is the package
        // spread over the month's working hours (per-day salary ÷ standard hours), and
        // each OT hour earns that rate uplifted by the company's day multiplier.
        double hourlyRate = stdHoursPerDay > 0 ? perDaySalary / stdHoursPerDay : 0.0;
        double overtimePay = overtimeHours * hourlyRate * companyOtMultiplier(employee);

        // Paid leave counts toward payable days; the worked-days figure already excludes
        // it (and unpaid leave). Payable days are capped at the reference month (22 with
        // no check-in data, else the period's working days) so a full month pays in full
        // and a partial month prorates as Gross / referenceDays × days worked.
        double payableDays = Math.min(workedDays + paidLeaveDays, referenceDays);
        double lopDays = Math.max(referenceDays - payableDays, 0.0);
        double lopAmount = lopDays * perDaySalary;
        // Gross earnings are the FULL monthly package; unpaid absence (LOP) is shown
        // as a deduction below rather than silently shrinking the gross, so the payslip
        // always reconciles: gross earnings − deductions = net pay.
        double grossEarnings = monthlyGross;

        boolean finalSettlement = isFinalSettlement(employee);

        // End-of-service gratuity is paid only on a final settlement (exiting employee).
        double endOfServiceCompensation = finalSettlement
                ? retirementCompensationService.computeAccruedAmount(employee).doubleValue()
                : 0.0;

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        // Amount the run can pay before loan recovery: full package + EOS + OT, less LOP.
        double availableBeforeLoan = grossEarnings + endOfServiceCompensation + overtimePay - lopAmount;

        double loanDeduction;
        if (finalSettlement) {
            // A final settlement clears the outstanding balance — but only up to what the
            // settlement can actually pay. Any shortfall stays owed on the loan rather than
            // being silently written off, and the net never goes negative.
            double outstanding = activeLoans.stream()
                    .mapToDouble(loan -> Math.max(safe(loan.getBalance()), 0.0))
                    .sum();
            loanDeduction = Math.min(outstanding, Math.max(availableBeforeLoan, 0.0));
        } else {
            // Normal runs recover one monthly installment per active loan.
            loanDeduction = activeLoans.stream()
                    .mapToDouble(loan -> Math.min(
                            safe(loan.getMonthlyDeduction()), Math.max(safe(loan.getBalance()), 0.0)))
                    .sum();
        }

        double totalDeductions = lopAmount + loanDeduction;
        double netPayable = (grossEarnings + endOfServiceCompensation + overtimePay) - totalDeductions;

        if (netPayable < 0) {
            netPayable = 0.0;
        }

        return new PayrollComputation(
                round2(monthlyGross),
                workingDays,
                round2(perDaySalary),
                round2(workedHours),
                round2(overtimeHours),
                round2(overtimePay),
                round2(workedDays),
                round2(paidLeaveDays),
                round2(unpaidLeaveDays),
                round2(payableDays),
                round2(lopDays),
                round2(lopAmount),
                round2(loanDeduction),
                round2(totalDeductions),
                round2(netPayable),
                round2(grossEarnings),
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
            throw new PayrollGenerationException(
                    "This employee has leave requests still pending approval for this period. "
                            + "Approve or reject them before generating payroll.");
        }
    }

    private void validateDuplicatePayroll(Employee employee, LocalDate payPeriodStart, LocalDate payPeriodEnd) {
        boolean exists = payrollRepo.existsByEmployeeAndPayPeriodStartAndPayPeriodEnd(
                employee,
                payPeriodStart,
                payPeriodEnd
        );

        if (exists) {
            throw new PayrollGenerationException(
                    "Payroll has already been generated for " + employeeLabel(employee)
                            + " for this pay period.");
        }
    }

    /** "employee name (EMP-001)" for use in user-facing payroll messages. */
    private String employeeLabel(Employee employee) {
        String name = ((employee.getFirstName() == null ? "" : employee.getFirstName())
                + " " + (employee.getLastName() == null ? "" : employee.getLastName())).trim();
        String no = employee.getEmployeeNo();
        if (name.isBlank()) {
            return no != null ? no : "this employee";
        }
        return no != null ? name + " (" + no + ")" : name;
    }

    private void applyLoanRecovery(
            Employee employee, boolean finalSettlement, double finalSettlementRecovery) {
        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        // For a final settlement, recover exactly the amount deducted on the payslip
        // ({@code loanDeduction}), drawn down loan by loan so balances reconcile to the
        // cent. Any residual beyond what the settlement could cover stays owed.
        double remaining = Math.max(finalSettlementRecovery, 0.0);

        for (EmployeeLoan loan : activeLoans) {
            double monthlyDeduction = safe(loan.getMonthlyDeduction());
            double balance = safe(loan.getBalance());

            double actualRecovery;
            if (finalSettlement) {
                actualRecovery = Math.min(balance, remaining);
                remaining -= actualRecovery;
            } else {
                actualRecovery = Math.min(monthlyDeduction, balance);
            }
            double newBalance = round2(Math.max(balance - actualRecovery, 0.0));
            loan.setBalance(newBalance);

            // Only close a loan that is genuinely cleared — a final settlement that could
            // not cover the balance leaves the residual owed and the loan still active.
            if (newBalance <= 0.0) {
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
                .orElseThrow(() -> new PayrollGenerationException(
                        "No active salary is set for " + employeeLabel(employee)
                                + ". Add a salary in the Salary tab before generating payroll."));
    }

    private EmployeeBankDetails getBankDetails(Employee employee) {
        return bankRepo.findByEmployee(employee)
                .orElseThrow(() -> new PayrollGenerationException(
                        "No bank details are set for " + employeeLabel(employee)
                                + ". Add them in the Bank tab before generating payroll."));
    }

    /**
     * Best-effort: email the generated payslip PDF to the employee. Never breaks payroll
     * generation — a missing email or unconfigured mail server is logged and skipped.
     */
    private void emailPayslip(Employee employee, Payroll payroll) {
        try {
            if (!emailService.isConfigured()) {
                return;
            }
            String email = employee.getUser() != null ? employee.getUser().getEmail() : null;
            if (email == null || email.isBlank()) {
                return;
            }
            byte[] pdf = payslipDocumentService.generatePayslipPdf(
                    employee.getId(), payroll.getPayrollCode());
            String subject = "Your payslip — " + payroll.getPayrollCode();
            String body = "Dear " + safeStr(employee.getFirstName())
                    + ",\n\nPlease find attached your payslip for the period "
                    + payroll.getPayPeriodStart() + " to " + payroll.getPayPeriodEnd()
                    + ".\n\nRegards,\nHR";
            emailService.sendWithPdfAttachment(
                    email, subject, body, pdf,
                    "payslip-" + payroll.getPayrollCode() + ".pdf");
        } catch (Exception e) {
            log.warn("Could not email payslip {}: {}",
                    payroll.getPayrollCode(), e.getMessage());
        }
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

        // Payroll may only be processed for a period that has already begun — the
        // current (ongoing) month is fine, but a period that has not started yet is
        // rejected. The pay date itself may be in the future (pay-later).
        if (dto.getPayPeriodStart().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Payroll cannot be processed for a future period that has not started yet.");
        }
    }

    /**
     * A final settlement cannot run beyond the employee's expected end date — the last
     * working day recorded on the current job. The date is mandatory for an exiting
     * employee; on preview it is only enforced once it has been set ({@code requireDate}
     * = false), so the breakdown can still be viewed while HR fills it in.
     */
    private void validateExitCutoff(
            Employee employee, PayrollGenerateRequestDTO dto, boolean requireDate) {
        if (!FINAL_SETTLEMENT_STATUSES.contains(employee.getStatus())) {
            return;
        }
        LocalDate expectedEnd = currentJobRepo.findByEmployee_Id(employee.getId())
                .map(job -> job.getExpectedEndDate())
                .orElse(null);
        if (expectedEnd == null) {
            if (requireDate) {
                throw new PayrollGenerationException(
                        "Set the employee's expected end date (last working day) on the "
                                + "profile or Current Job tab before processing the final settlement.");
            }
            return;
        }
        if (dto.getPayPeriodEnd().isAfter(expectedEnd)) {
            throw new PayrollGenerationException(
                    "Payroll cannot be processed beyond the expected end date (" + expectedEnd + ").");
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

    /** Company's standard full-day length in hours; falls back to {@link #STANDARD_HOURS_PER_DAY}. */
    private double companyStandardHours(com.erp.domain.Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getStandardWorkingHoursPerDay() != null) {
                return employee.getCompany().getStandardWorkingHoursPerDay().doubleValue();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — use default
        }
        return STANDARD_HOURS_PER_DAY;
    }

    /** Daytime overtime multiplier from company policy (default 1.25). */
    private double companyOtMultiplier(com.erp.domain.Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getOtDayRateMultiplier() != null) {
                double m = employee.getCompany().getOtDayRateMultiplier().doubleValue();
                if (m > 0) {
                    return m;
                }
            }
        } catch (Exception ignored) {
            // lazy company not loadable — use default
        }
        return 1.25;
    }

    /** Whether the company punches in/out (default true). */
    private boolean companyRequireCheckIn(com.erp.domain.Employee employee) {
        try {
            if (employee.getCompany() != null) {
                return employee.getCompany().isRequireCheckIn();
            }
        } catch (Exception ignored) {
            // lazy company not loadable — assume required
        }
        return true;
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
        // Qatar workweek: Sunday–Thursday, with Friday & Saturday as the weekend.
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
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
                computation.grossEarnings(),
                computation.overtimePay(),
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
        // Derive gross/deductions from the invariant fields (net, LOP, loan) so rows
        // generated before LOP moved into the deductions bucket still reconcile:
        // gross earnings = net + all deductions; deductions = loss of pay + loans.
        double lop = payroll.getLopAmount() != null ? payroll.getLopAmount() : 0.0;
        double loan = payroll.getLoanDeduction() != null ? payroll.getLoanDeduction() : 0.0;
        double net = payroll.getNetPayable() != null ? payroll.getNetPayable() : 0.0;
        double totalDeductions = round2(lop + loan);
        dto.setGrossPay(round2(net + totalDeductions));
        dto.setLoanDeduction(payroll.getLoanDeduction());
        dto.setTotalDeductions(totalDeductions);
        dto.setNetPayable(payroll.getNetPayable());
        dto.setEndOfServiceCompensation(payroll.getEndOfServiceCompensation());
        dto.setOvertimeHours(payroll.getOvertimeHours());
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
            double overtimeHours,
            double overtimePay,
            double workedDays,
            double paidLeaveDays,
            double unpaidLeaveDays,
            double payableDays,
            double lopDays,
            double lopAmount,
            double loanDeduction,
            double totalDeductions,
            double netPayable,
            double grossEarnings,
            double endOfServiceCompensation,
            boolean finalSettlement
    ) {
    }
}