package com.erp.service.salary;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.exception.PayrollGenerationException;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeave;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.EmployeeBenefitGrant;
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
import java.time.temporal.ChronoUnit;
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
    private final com.erp.service.LeavePolicyKeyResolver leaveKeyResolver;
    private final PayrollRepository payrollRepo;
    private final DocumentSequenceService documentSequenceService;
    private final RetirementCompensationService retirementCompensationService;
    private final ProcessAccountDefaultsService processAccountDefaultsService;
    private final TransactionService transactionService;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuthContext authContext;
    private final PayslipDocumentService payslipDocumentService;
    private final com.erp.service.notification.EmailService emailService;
    private final com.erp.service.hr.EmployeeSeparationService separationService;
    private final BenefitGrantService benefitGrantService;

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

        double grossPay = round2(computation.totalGross());
        // The funds check should reflect what actually posts to the ledger — the earned
        // salary expense (gross minus loss of pay), not the pre-LOP gross.
        double payrollExpense = round2(grossPay - computation.lopAmount());
        PayrollAccountStatusDTO accountStatus = resolvePayrollAccountStatus(
                employee.getCompanyId(), payrollExpense);

        return toPreviewDTO(computation, grossPay, accountStatus,
                PayPeriodMath.monthFactor(dto.getPayPeriodStart(), dto.getPayPeriodEnd()));
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
        benefitGrantService.markPaid(computation.benefitGrants(), saved.getId());

        applyLoanRecovery(
                employee,
                computation.finalSettlement(),
                computation.loanDeduction(),
                dto.getPayPeriodStart(),
                dto.getPayPeriodEnd());
        postPayrollToAccounting(saved, employee);
        emailPayslip(employee, saved);

        // A final settlement is the employee's last run. They move to INACTIVE (and
        // drop out of the operational lists) only once their exit interview is also
        // submitted — until then they stay visible so HR can finish the separation.
        if (computation.finalSettlement()) {
            separationService.completeIfReady(employee);
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
            benefitGrantService.markPaid(computation.benefitGrants(), saved.getId());

            applyLoanRecovery(
                    employee,
                    computation.finalSettlement(),
                    computation.loanDeduction(),
                    dto.getPayPeriodStart(),
                    dto.getPayPeriodEnd());
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
        return getPayrollHistory(employeeId, false);
    }

    /**
     * Payroll processing works with the current month's run; full history is
     * reserved for the employee history screen and reporting workflows.
     */
    @Transactional(readOnly = true)
    public List<PayrollHistoryDTO> getPayrollHistory(Long employeeId, boolean includeAll) {
        Employee employee = getEmployee(employeeId);
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        return payrollRepo.findByEmployeeOrderByPayDateDesc(employee)
                .stream()
                .filter(payroll -> includeAll || (payroll.getPayDate() != null
                        && !payroll.getPayDate().isBefore(monthStart)
                        && !payroll.getPayDate().isAfter(monthEnd)))
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
                computation.totalGross(),
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
        payroll.setGrossPay(round2(computation.totalGross()));
        payroll.setBenefitsAmount(computation.benefitsAmount());
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
        // configured End-of-Service accounts as its own ledger entry; the rest of the
        // package posts to the payroll account. If no EOS debit is configured, the
        // whole amount posts to payroll as before.
        // When an EOSB credit (liability) account is set, settlement debits that
        // provision and credits payroll payable; otherwise debit the EOS expense
        // account (legacy) against payroll credit.
        Long eosDebitAccountId = eos > 0
                ? processAccountDefaultsService.resolveEndOfServiceAccountId(companyId)
                : null;
        Long eosCreditAccountId = eos > 0
                ? processAccountDefaultsService.resolveEndOfServiceCreditAccountId(companyId)
                : null;

        if (eosDebitAccountId != null || eosCreditAccountId != null) {
            BigDecimal regular = BigDecimal.valueOf(round2(payroll.getGrossPay() - eos - lop));
            transactionService.recordPayrollPosting(
                    companyId, payroll.getId(), regular, debitAccountId, creditAccountId, desc);
            Long settlementDebit = eosCreditAccountId != null ? eosCreditAccountId : eosDebitAccountId;
            transactionService.recordEndOfServicePosting(
                    companyId, payroll.getId(), BigDecimal.valueOf(round2(eos)),
                    settlementDebit, creditAccountId,
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

        // Gross earnings for the period: monthlyGross is a per-calendar-month figure, so a
        // period is walked one calendar month at a time and each month's share is weighted
        // by (days of that month inside the period) / (actual days in that month — 28, 29,
        // 30 or 31). A period covering exactly N whole months totals N × monthlyGross; a
        // final-settlement period spanning several unpaid months (e.g. Aug 1 – Sep 30) is
        // no longer flattened down to a single month's pay.
        double grossEarnings = prorateAcrossCalendarMonths(monthlyGross, periodStart, periodEnd);

        // Daily rate = the period's gross spread over the period's ACTUAL working days
        // (Sun–Thu), for every organisation. Using the real count (20–23 per month)
        // rather than a flat 22 means a month of full attendance always pays in full —
        // no phantom loss-of-pay in 20/21-day months, and no unpaid day silently
        // absorbed in 23-day months. Multi-month periods stay consistent because both
        // gross and working days span the same window.
        boolean requireCheckIn = companyRequireCheckIn(employee);
        double referenceDays = workingDays;
        double perDaySalary = grossEarnings / referenceDays;

        // Working days before the employee joined are not payable: a mid-period joiner
        // is prorated to the days they were actually employed.
        LocalDate joinDate = resolveJoinDate(employee);
        LocalDate employedFrom = joinDate != null && joinDate.isAfter(periodStart) ? joinDate : periodStart;
        int notEmployedDays = employedFrom.isAfter(periodEnd)
                ? workingDays
                : countWorkingDays(periodStart, employedFrom.minusDays(1));
        int employedWorkingDays = Math.max(workingDays - notEmployedDays, 0);

        // Approved leaves within the (employed part of the) period, split into paid vs
        // unpaid and counted in WORKING days — the same unit as the daily rate — so a
        // leave recorded "incl. weekends" doesn't over-deduct Fridays/Saturdays.
        List<EmployeeLeave> approvedLeaves = employedFrom.isAfter(periodEnd)
                ? List.of()
                : leaveRepo.findApprovedLeavesForPayrollPeriod(
                        employee.getId(),
                        employedFrom,
                        periodEnd
                );

        double paidLeaveDays = 0.0;
        double unpaidLeaveDays = 0.0;
        for (EmployeeLeave leave : approvedLeaves) {
            double leaveDays = calculateLeaveDaysWithinPeriod(leave, employedFrom, periodEnd);
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
            // Organisation doesn't punch in/out — every employed working day is paid
            // EXCEPT approved unpaid leave. Worked days = employed working days minus all
            // leave; paid leave is added back in payableDays below so only unpaid leave
            // (and days before joining) reduce pay.
            workedDays = Math.max(employedWorkingDays - paidLeaveDays - unpaidLeaveDays, 0.0);
            workedHours = workedDays * stdHoursPerDay;
        } else {
            // Align exactly with the Attendance History report: worked hours = sum of
            // logged time; worked days = days that reached a full standard day (the
            // report's "Days Worked" / daysPresent), not a fractional hours/standard.
            long totalMinutes = timesheets.stream()
                    .mapToLong(this::resolveWorkedMinutes)
                    .sum();
            workedHours = totalMinutes / 60.0;
            // Only Sun–Thu punches count toward worked days: the salary covers the
            // working week, so a Friday/Saturday punch is rest-day overtime (below),
            // never a substitute for a missed working day.
            workedDays = timesheets.stream()
                    .filter(t -> t.getAttendanceDate() != null && !isWeekend(t.getAttendanceDate()))
                    .filter(t -> resolveWorkedMinutes(t) >= stdMinutes)
                    .count();
        }

        // Overtime = hours logged beyond the standard for the whole period. In a
        // no-punch organisation there are no punches to derive it from, so it comes
        // from the manual monthly override HR keyed in the Time Sheets tab (0 if none),
        // summed across every calendar month the period touches — a multi-month
        // settlement period must not lose the later months' overrides.
        double overtimeHours;
        double restDayOvertimeHours = 0.0;
        if (!requireCheckIn) {
            overtimeHours = sumOvertimeOverrideHours(employee.getId(), periodStart, periodEnd);
        } else {
            // Per DAY: hours beyond the standard day, capped at the company's daily
            // overtime limit — exactly how the Time Sheets board computes it. (Netting
            // total hours against the period's standard let absences cancel out genuine
            // overtime and ignored the daily cap.) On a rest day (Fri/Sat) every hour
            // worked is overtime, capped at the maximum working day (standard + OT cap).
            long otCapMinutes = Math.round(companyOtMaxHours(employee) * 60.0);
            long weekdayOtMinutes = 0L;
            long restDayMinutes = 0L;
            for (EmployeeTimesheet t : timesheets) {
                long minutes = resolveWorkedMinutes(t);
                if (t.getAttendanceDate() != null && isWeekend(t.getAttendanceDate())) {
                    restDayMinutes += Math.min(Math.max(minutes, 0L), stdMinutes + otCapMinutes);
                } else {
                    long over = minutes - stdMinutes;
                    weekdayOtMinutes += over <= 0 ? 0L : Math.min(over, otCapMinutes);
                }
            }
            restDayOvertimeHours = restDayMinutes / 60.0;
            overtimeHours = (weekdayOtMinutes + restDayMinutes) / 60.0;
        }

        // Overtime is paid ON TOP of the monthly package: hourly rate is the package
        // spread over the month's working hours (per-day salary ÷ standard hours).
        // Working-day OT earns the company's day multiplier; rest-day (Fri/Sat) work
        // earns the Friday/holiday multiplier.
        double hourlyRate = stdHoursPerDay > 0 ? perDaySalary / stdHoursPerDay : 0.0;
        double overtimePay = (overtimeHours - restDayOvertimeHours) * hourlyRate * companyOtMultiplier(employee)
                + restDayOvertimeHours * hourlyRate * companyRestDayOtMultiplier(employee);

        // Paid leave counts toward payable days; the worked-days figure already excludes
        // it (and unpaid leave). Payable days are capped at the days the employee was
        // employed in the period, so a full month of attendance pays in full and loss of
        // pay = unpaid leave + days not worked/employed, at Gross / working days per day.
        double payableDays = Math.min(workedDays + paidLeaveDays, employedWorkingDays);
        double lopDays = Math.max(referenceDays - payableDays, 0.0);
        double lopAmount = lopDays * perDaySalary;
        // Gross earnings (computed above) are the FULL package for the period; unpaid
        // absence (LOP) is shown as a deduction below rather than silently shrinking the
        // gross, so the payslip always reconciles: gross earnings − deductions = net pay.

        boolean finalSettlement = isFinalSettlement(employee);

        // End-of-service gratuity is paid only on a final settlement (exiting employee).
        double endOfServiceCompensation = finalSettlement
                ? retirementCompensationService.computeAccruedAmount(employee).doubleValue()
                : 0.0;

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        // One-off benefit grants (annual ticket, bonus, reimbursement) still unpaid for
        // this month or earlier are paid on top of the package in this run.
        List<EmployeeBenefitGrant> benefitGrants =
                benefitGrantService.pendingForPeriod(employee.getId(), periodEnd);
        double benefitsAmount = benefitGrants.stream()
                .mapToDouble(g -> g.getAmount() != null ? g.getAmount().doubleValue() : 0.0)
                .sum();

        // Amount the run can pay before loan recovery: full package + EOS + OT + benefits, less LOP.
        double availableBeforeLoan = grossEarnings + endOfServiceCompensation + overtimePay
                + benefitsAmount - lopAmount;

        // How many calendar-month equivalents the period covers (2.0 for two full months,
        // ~2.5 for a 2.5-month window). Used to scale monthly loan installments.
        double monthFactor = prorateAcrossCalendarMonths(1.0, periodStart, periodEnd);

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
            // Normal runs recover one monthly installment per active loan, scaled across
            // the full pay period so a 2–3 month window deducts 2–3 installments (capped
            // at each loan's remaining balance).
            loanDeduction = activeLoans.stream()
                    .mapToDouble(loan -> Math.min(
                            safe(loan.getMonthlyDeduction()) * monthFactor,
                            Math.max(safe(loan.getBalance()), 0.0)))
                    .sum();
        }

        double totalDeductions = lopAmount + loanDeduction;
        double netPayable = (grossEarnings + endOfServiceCompensation + overtimePay + benefitsAmount)
                - totalDeductions;

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
                finalSettlement,
                round2(benefitsAmount),
                benefitGrants
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

        // Payroll pays per WORKING day, so only working days of the leave count here —
        // even when the leave itself was recorded "including weekends".
        return countWorkingDays(effectiveStart, effectiveEnd);
    }

    /** Employee's join date, falling back to the current job's start date. */
    private LocalDate resolveJoinDate(Employee employee) {
        if (employee.getJoinDate() != null) {
            return employee.getJoinDate();
        }
        return currentJobRepo.findByEmployee_Id(employee.getId())
                .map(job -> job.getStartDate())
                .orElse(null);
    }

    /** Company's maximum overtime hours per day (default 2). */
    private double companyOtMaxHours(Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getOtMaxHoursPerDay() != null) {
                double v = employee.getCompany().getOtMaxHoursPerDay().doubleValue();
                if (v >= 0) {
                    return v;
                }
            }
        } catch (Exception ignored) {
            // lazy company not loadable — use default
        }
        return 2.0;
    }

    private boolean isPaidLeave(Employee employee, String leaveType) {
        if (employee == null || employee.getCompany() == null) {
            return false;
        }

        // Leave policies are keyed by job code (with role fallbacks) — match any of
        // the employee's resolved keys. See LeavePolicyKeyResolver.
        List<String> keys = leaveKeyResolver.keysFor(employee);
        if (keys.isEmpty()) {
            return false;
        }

        return leavePolicyRepo.findByCompanyOrderByIdDesc(employee.getCompany())
                .stream()
                .filter(policy -> keys.stream().anyMatch(k -> same(policy.getJobCode(), k)))
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

    /**
     * A pay period may not overlap any payroll already processed for the employee —
     * otherwise a multi-month run (e.g. Jun–Aug) and a later single-month run (Jul)
     * would pay the same days twice.
     */
    private void validateDuplicatePayroll(Employee employee, LocalDate payPeriodStart, LocalDate payPeriodEnd) {
        List<Payroll> overlapping = payrollRepo
                .findByEmployeeAndPayPeriodStartLessThanEqualAndPayPeriodEndGreaterThanEqual(
                        employee, payPeriodEnd, payPeriodStart);

        if (!overlapping.isEmpty()) {
            Payroll existing = overlapping.get(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            throw new PayrollGenerationException(
                    "Payroll has already been processed for " + employeeLabel(employee)
                            + " covering " + existing.getPayPeriodStart().format(fmt)
                            + " – " + existing.getPayPeriodEnd().format(fmt)
                            + " (" + existing.getPayrollCode() + "). The new pay period must not "
                            + "overlap it — start it on "
                            + existing.getPayPeriodEnd().plusDays(1).format(fmt)
                            + " or later, or end it before "
                            + existing.getPayPeriodStart().format(fmt) + ".");
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
            Employee employee,
            boolean finalSettlement,
            double periodLoanDeduction,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, STATUS_ACTIVE);

        // For a final settlement, recover exactly the amount deducted on the payslip
        // ({@code loanDeduction}), drawn down loan by loan so balances reconcile to the
        // cent. Any residual beyond what the settlement could cover stays owed.
        double remaining = Math.max(periodLoanDeduction, 0.0);
        double monthFactor = finalSettlement
                ? 1.0
                : prorateAcrossCalendarMonths(1.0, periodStart, periodEnd);

        for (EmployeeLoan loan : activeLoans) {
            double monthlyDeduction = safe(loan.getMonthlyDeduction());
            double balance = safe(loan.getBalance());

            double actualRecovery;
            if (finalSettlement) {
                actualRecovery = Math.min(balance, remaining);
                remaining -= actualRecovery;
            } else {
                // Match computePayroll: installment × period month-factor, capped at balance.
                actualRecovery = Math.min(monthlyDeduction * monthFactor, balance);
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

        // A period may span several past months but can run only up to the end of the
        // current month — never into a future month.
        LocalDate currentMonthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        if (dto.getPayPeriodEnd().isAfter(currentMonthEnd)) {
            throw new IllegalArgumentException(
                    "Payroll can be processed for past months up to the current month only. "
                            + "The pay period cannot end after "
                            + currentMonthEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
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

    /** OT multiplier for rest-day (Fri/Sat) and holiday work (default 1.50). */
    private double companyRestDayOtMultiplier(com.erp.domain.Employee employee) {
        try {
            if (employee.getCompany() != null
                    && employee.getCompany().getOtNightFridayHolidayRateMultiplier() != null) {
                double m = employee.getCompany().getOtNightFridayHolidayRateMultiplier().doubleValue();
                if (m > 0) {
                    return m;
                }
            }
        } catch (Exception ignored) {
            // lazy company not loadable — use default
        }
        return 1.50;
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

    /**
     * Spreads a flat "per calendar month" figure (e.g. monthlyGross, or a monthly loan
     * installment) across {@code [start, end]} by walking one calendar month at a
     * time and weighting each month's contribution by (days of that month inside the
     * period) / (actual days in that month — 28, 29, 30 or 31, per {@link LocalDate#lengthOfMonth()}).
     * A period covering exactly N whole calendar months yields N × value; a partial
     * month (e.g. a 24- or 36-day settlement period) yields that month's exact
     * fractional share rather than assuming a flat 30-day month.
     */
    private double prorateAcrossCalendarMonths(double perMonthValue, LocalDate start, LocalDate end) {
        return perMonthValue * PayPeriodMath.monthFactor(start, end);
    }

    /**
     * Sums the manual monthly overtime override (Time Sheets tab, no-punch organisations
     * only) for every calendar month the pay period touches, prorating each month's
     * override by the share of that month inside the period — same weighting as gross.
     */
    private double sumOvertimeOverrideHours(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        double total = 0.0;
        LocalDate segmentStart = periodStart;
        while (!segmentStart.isAfter(periodEnd)) {
            LocalDate segmentMonthEnd = segmentStart.withDayOfMonth(segmentStart.lengthOfMonth());
            LocalDate segmentEnd = segmentMonthEnd.isBefore(periodEnd) ? segmentMonthEnd : periodEnd;
            long daysInSegment = ChronoUnit.DAYS.between(segmentStart, segmentEnd) + 1;
            int daysInMonth = segmentStart.lengthOfMonth();
            double monthHours = overtimeOverrideRepo
                    .findByEmployee_IdAndYearAndMonth(
                            employeeId, segmentStart.getYear(), segmentStart.getMonthValue())
                    .map(o -> Math.max(0.0, o.getOvertimeHours()))
                    .orElse(0.0);
            total += monthHours * daysInSegment / daysInMonth;
            segmentStart = segmentEnd.plusDays(1);
        }
        return total;
    }

    private PayrollPreviewDTO toPreviewDTO(
            PayrollComputation computation,
            double grossPay,
            PayrollAccountStatusDTO payrollAccount,
            double periodMonths) {
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
                payrollAccount,
                computation.benefitsAmount(),
                Math.round(periodMonths * 100.0) / 100.0
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
            boolean finalSettlement,
            double benefitsAmount,
            List<EmployeeBenefitGrant> benefitGrants
    ) {
        /** Everything the run pays before deductions: package + EOS + overtime + benefits. */
        public double totalGross() {
            return grossEarnings + endOfServiceCompensation + overtimePay + benefitsAmount;
        }
    }
}
