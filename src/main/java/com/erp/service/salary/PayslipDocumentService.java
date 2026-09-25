package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.enums.BenefitType;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.Payroll;
import com.erp.dto.salary.PayslipDocumentDTO;
import com.erp.dto.salary.PayslipDocumentDTO.LineItemDTO;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.security.context.AuthContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PayslipDocumentService {

    private static final String CURRENCY_WARNING =
            "Set currency from company profile to enable currency display.";

    private final EmployeeRepository employeeRepo;
    private final PayrollRepository payrollRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeLoanRepository loanRepo;
    private final EmployeeBankDetailsRepository bankRepo;
    private final com.erp.repo.EmployeeCurrentJobRepo currentJobRepo;
    private final com.erp.repo.salary.EmployeeBenefitGrantRepository benefitGrantRepo;
    private final TemplateEngine templateEngine;
    private final AuthContext authContext;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generatePayslipPdf(Long employeeId, String payrollCode) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);

        Payroll payroll = payrollRepo.findByEmployeeAndPayrollCode(employee, payrollCode)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        EmployeeCompensation compensation = compensationRepo.findActiveByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("No active compensation found"));

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");

        EmployeeBankDetails bank = bankRepo.findByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("Bank details not found"));

        PayslipDocumentDTO dto = buildDTO(employee, payroll, compensation, activeLoans, bank);
        String html = renderHtml(dto);
        return renderPdf(html);
    }

    private PayslipDocumentDTO buildDTO(
            Employee employee,
            Payroll payroll,
            EmployeeCompensation compensation,
            List<EmployeeLoan> activeLoans,
            EmployeeBankDetails bank) {

        PayslipDocumentDTO dto = new PayslipDocumentDTO();

        dto.setEmployeeNo(employee.getEmployeeNo());
        dto.setFirstName(employee.getFirstName());
        dto.setLastName(employee.getLastName());
        dto.setDepartment(employee.getDepartment() != null
                ? employee.getDepartment().getDepartmentName()
                : "—");
        dto.setDesignation(resolveDesignation(employee));
        dto.setStatus(humanizeStatus(employee.getStatus()));
        dto.setDateOfJoining(employee.getJoinDate());

        if (employee.getCompany() != null && employee.getCompany().getCurrency() != null) {
            dto.setCurrencyCode(employee.getCompany().getCurrency().getCurrencyCode());
            dto.setCurrencySymbol(employee.getCompany().getCurrency().getCurrencySymbol());
            dto.setCurrencyConfigured(true);
            dto.setCurrencyWarning(null);
        } else {
            dto.setCurrencyCode(null);
            dto.setCurrencySymbol(null);
            dto.setCurrencyConfigured(false);
            dto.setCurrencyWarning(CURRENCY_WARNING);
        }

        dto.setPayrollCode(payroll.getPayrollCode());
        dto.setPayPeriodStart(payroll.getPayPeriodStart());
        dto.setPayPeriodEnd(payroll.getPayPeriodEnd());
        dto.setPayDate(payroll.getPayDate());

        dto.setWorkingDays(payroll.getWorkingDays());
        dto.setTotalDays(payroll.getTotalDays());
        dto.setLeaveTaken(payroll.getLeaveTaken());
        dto.setWorkedDays(payroll.getWorkedDays());
        dto.setWorkedHours(payroll.getWorkedHours());
        dto.setOvertimeHours(payroll.getOvertimeHours());
        dto.setPaidLeaveDays(payroll.getPaidLeaveDays());
        dto.setUnpaidLeaveDays(payroll.getUnpaidLeaveDays());
        dto.setPayableDays(payroll.getPayableDays());
        dto.setLopDays(payroll.getLopDays());
        dto.setLopAmount(payroll.getLopAmount());

        // The compensation record holds MONTHLY amounts; a pay period can span several
        // months (e.g. Jun 1 – Aug 31), so each component is scaled by the months the
        // period covers — the lines then add up to the period's gross, not one month's.
        double monthFactor = PayPeriodMath.monthFactor(
                payroll.getPayPeriodStart(), payroll.getPayPeriodEnd());
        if (monthFactor <= 0) {
            monthFactor = 1.0;
        }
        List<LineItemDTO> earnings = buildEarnings(compensation, monthFactor);
        // Overtime is paid on top of the monthly package — show it as its own earnings
        // line so the gross reconciles (basic + allowances + overtime + gratuity = gross).
        if (payroll.getOvertimePay() != null && payroll.getOvertimePay() > 0) {
            earnings.add(line("Overtime Pay", payroll.getOvertimePay()));
        }
        if (payroll.getEndOfServiceCompensation() != null && payroll.getEndOfServiceCompensation() > 0) {
            earnings.add(line("End of Service Compensation", payroll.getEndOfServiceCompensation()));
        }
        // One-off benefit grants paid in this run (annual ticket, bonus, reimbursement).
        if (payroll.getId() != null && payroll.getBenefitsAmount() != null && payroll.getBenefitsAmount() > 0) {
            for (com.erp.domain.salary.EmployeeBenefitGrant grant : benefitGrantRepo.findByPayrollId(payroll.getId())) {
                if (grant.getAmount() != null && grant.getBenefitType() != null) {
                    earnings.add(line(grant.getBenefitType().getLabel(), grant.getAmount().doubleValue()));
                }
            }
        }
        dto.setEarnings(earnings);
        dto.setDeductions(buildDeductions(activeLoans, payroll));

        // Derive the summary from invariant fields (net, LOP, loan) so the payslip always
        // reconciles — gross earnings − deductions = net — for rows generated both before
        // and after loss of pay moved into the deductions bucket.
        double lopAmt = payroll.getLopAmount() != null ? payroll.getLopAmount() : 0.0;
        double loanAmt = payroll.getLoanDeduction() != null ? payroll.getLoanDeduction() : 0.0;
        double net = payroll.getNetPayable() != null ? payroll.getNetPayable() : 0.0;
        double totalDeductions = Math.round((lopAmt + loanAmt) * 100.0) / 100.0;
        dto.setGrossPay(Math.round((net + totalDeductions) * 100.0) / 100.0);
        dto.setTotalDeductions(totalDeductions);
        dto.setNetPayable(net);

        dto.setBankName(bank.getBankName());
        dto.setBankBranch(bank.getBankBranch());
        dto.setAccountNo(bank.getAccountNo());

        return dto;
    }

    private List<LineItemDTO> buildEarnings(EmployeeCompensation c, double monthFactor) {
        List<LineItemDTO> list = new ArrayList<>();
        // "Basic Salary" for a one-month period; "Basic Salary (3 months)" otherwise.
        String suffix = PayPeriodMath.isSingleMonth(monthFactor)
                ? ""
                : " (" + PayPeriodMath.describeMonths(monthFactor) + ")";

        list.add(line("Basic Salary" + suffix, scaled(c.getBasicSalary(), monthFactor)));

        // Only cash allowances appear as line items. COMPANY_PROVIDED benefits are
        // stored as 0 on the compensation record and are not paid via payroll.
        if (c.getHousingType() == BenefitType.ALLOWANCE && amt(c.getHousingAllowance()) > 0) {
            list.add(line("Housing Allowance" + suffix, scaled(c.getHousingAllowance(), monthFactor)));
        }

        if (amt(c.getFoodAllowance()) > 0) {
            list.add(line("Food Allowance" + suffix, scaled(c.getFoodAllowance(), monthFactor)));
        }

        if (c.getTransportationType() == BenefitType.ALLOWANCE && amt(c.getTransportationAllowance()) > 0) {
            list.add(line("Transport Allowance" + suffix, scaled(c.getTransportationAllowance(), monthFactor)));
        }

        if (c.getTravelType() == BenefitType.ALLOWANCE && amt(c.getTravelAllowance()) > 0) {
            list.add(line("Travel Allowance" + suffix, scaled(c.getTravelAllowance(), monthFactor)));
        }

        if (amt(c.getOtherAllowance()) > 0) {
            list.add(line("Other Allowance" + suffix, scaled(c.getOtherAllowance(), monthFactor)));
        }

        return list;
    }

    /** Monthly amount × months in the pay period, rounded to cents. */
    private static double scaled(Double monthly, double monthFactor) {
        return Math.round(amt(monthly) * monthFactor * 100.0) / 100.0;
    }

    private static double amt(Double value) {
        return value != null ? value : 0.0;
    }

    private List<LineItemDTO> buildDeductions(List<EmployeeLoan> loans, Payroll payroll) {
        List<LineItemDTO> list = new ArrayList<>();

        // Loss of pay for unpaid absence — shown as a deduction so the payslip reconciles
        // (gross earnings − deductions = net pay) rather than silently reducing the gross.
        double lopAmount = payroll.getLopAmount() != null ? payroll.getLopAmount() : 0.0;
        if (lopAmount > 0) {
            double lopDays = payroll.getLopDays() != null ? payroll.getLopDays() : 0.0;
            String label = lopDays > 0
                    ? "Loss of Pay (" + trimDays(lopDays) + (lopDays == 1.0 ? " day)" : " days)")
                    : "Loss of Pay";
            list.add(line(label, lopAmount));
        }

        // On a final settlement the loans are recovered in full and closed during the
        // run, so they no longer appear as ACTIVE here — show the settled total instead.
        if (payroll.isFinalSettlement()) {
            double settled = payroll.getLoanDeduction() != null ? payroll.getLoanDeduction() : 0.0;
            if (settled > 0) {
                list.add(line("Loan Settlement (full)", settled));
            }
            return list;
        }

        if (loans == null || loans.isEmpty()) {
            double stored = payroll.getLoanDeduction() != null ? payroll.getLoanDeduction() : 0.0;
            if (stored > 0) {
                list.add(line("Loan deduction", stored));
            }
            return list;
        }

        // Scale each loan's monthly installment to the period total already stored on the
        // payroll (covers multi-month pay periods). Cap display at the stored total.
        double storedTotal = payroll.getLoanDeduction() != null ? payroll.getLoanDeduction() : 0.0;
        double monthlySum = loans.stream()
                .filter(l -> l.getMonthlyDeduction() > 0)
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();
        double scale = monthlySum > 0 ? storedTotal / monthlySum : 0.0;

        loans.stream()
                .filter(l -> l.getMonthlyDeduction() > 0)
                .forEach(l -> {
                    double amount = round2(l.getMonthlyDeduction() * scale);
                    if (amount > 0) {
                        list.add(line(
                                "Loan " + l.getLoanCode() + " (" + l.getLoanType().getName() + ")",
                                amount));
                    }
                });

        return list;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String esc(String value) {
        return value != null ? HtmlUtils.htmlEscape(value) : "—";
    }

    /** Render a day count without a trailing ".0" (2.0 → "2", 1.5 → "1.5"). */
    private String trimDays(double days) {
        return days == Math.rint(days)
                ? String.valueOf((long) days)
                : String.valueOf(Math.round(days * 10.0) / 10.0);
    }

    private String renderHtml(PayslipDocumentDTO dto) {
        Context ctx = new Context();

        ctx.setVariable("employeeNo", esc(dto.getEmployeeNo()));
        ctx.setVariable("firstName", esc(dto.getFirstName()));
        ctx.setVariable("lastName", esc(dto.getLastName()));
        ctx.setVariable("department", esc(dto.getDepartment()));
        ctx.setVariable("designation", esc(dto.getDesignation()));
        ctx.setVariable("status", esc(dto.getStatus()));
        ctx.setVariable("dateOfJoining", formatDate(dto.getDateOfJoining()));

        ctx.setVariable("workingDays", dto.getWorkingDays());
        ctx.setVariable("totalDays", dto.getTotalDays());
        ctx.setVariable("leaveTaken", dto.getLeaveTaken());
        ctx.setVariable("workedDays", dto.getWorkedDays());
        ctx.setVariable("workedHours", dto.getWorkedHours());
        ctx.setVariable("overtimeHours", dto.getOvertimeHours());
        ctx.setVariable("paidLeaveDays", dto.getPaidLeaveDays());
        ctx.setVariable("unpaidLeaveDays", dto.getUnpaidLeaveDays());
        ctx.setVariable("payableDays", dto.getPayableDays());
        ctx.setVariable("lopDays", dto.getLopDays());
        ctx.setVariable("lopAmount", dto.getLopAmount());

        ctx.setVariable("currencyCode", esc(dto.getCurrencyCode()));
        ctx.setVariable("currencySymbol", esc(dto.getCurrencySymbol()));

        ctx.setVariable("payrollCode", esc(dto.getPayrollCode()));
        ctx.setVariable("payPeriod", esc(formatPayPeriod(dto.getPayPeriodStart())));
        // Exact start-to-end dates for the "Pay Period" field (instead of just the month).
        ctx.setVariable("payPeriodRange", esc(
                formatDate(dto.getPayPeriodStart()) + " to " + formatDate(dto.getPayPeriodEnd())));
        ctx.setVariable("payDate", esc(formatDate(dto.getPayDate())));

        ctx.setVariable("earnings", escapeLineItems(dto.getEarnings()));
        ctx.setVariable("deductions", escapeLineItems(dto.getDeductions()));

        ctx.setVariable("grossPay", dto.getGrossPay());
        ctx.setVariable("totalDeductions", dto.getTotalDeductions());
        ctx.setVariable("netPayable", dto.getNetPayable());

        ctx.setVariable("bankName", esc(dto.getBankName()));
        ctx.setVariable("bankBranch", esc(dto.getBankBranch()));
        ctx.setVariable("maskedAccount", esc(maskAccount(dto.getAccountNo())));

        ctx.setVariable("generatedAt", LocalDate.now().format(DATE_FMT));

        return templateEngine.process("payslip", ctx);
    }

    private byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Payslip PDF generation failed", e);
        }
    }

    private List<LineItemDTO> escapeLineItems(List<LineItemDTO> items) {
        if (items == null) return List.of();
        return items.stream().map(i -> {
            LineItemDTO escaped = new LineItemDTO();
            escaped.setLabel(HtmlUtils.htmlEscape(i.getLabel()));
            escaped.setAmount(i.getAmount());
            return escaped;
        }).toList();
    }

    private LineItemDTO line(String label, double amount) {
        LineItemDTO item = new LineItemDTO();
        item.setLabel(label);
        item.setAmount(amount);
        return item;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "—";
    }

    private String formatPayPeriod(LocalDate date) {
        if (date == null) return "—";
        return date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.getYear();
    }

    private String maskAccount(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) return "—";
        String clean = accountNo.replaceAll("[-\\s]", "");
        if (clean.length() < 8) return clean;
        return clean.substring(0, 4) + " •••• " + clean.substring(clean.length() - 4);
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

    /** Enum name → title case, e.g. UNDER_PROBATION → "Under Probation". */
    private String humanizeStatus(com.erp.domain.EmployeeStatus s) {
        if (s == null) return "—";
        String[] parts = s.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Designation on the payslip is the employee's Job Title (the current job's job-code
     * title), NOT the system/security role. Falls back to the company role only when no
     * job title is on record.
     */
    private String resolveDesignation(Employee employee) {
        if (employee == null) return "—";

        if (employee.getId() != null) {
            String jobTitle = currentJobRepo.findByEmployee_Id(employee.getId())
                    .map(job -> job.getJobCode() != null ? job.getJobCode().getTitle() : null)
                    .orElse(null);
            if (jobTitle != null && !jobTitle.isBlank()) {
                return jobTitle.trim();
            }
        }

        String companyRole = employee.getCompanyRole();
        if (companyRole != null && !companyRole.isBlank()) {
            return companyRole.trim();
        }

        String role = employee.getRole();
        if (role != null && !role.isBlank()) {
            return role.trim();
        }

        return "—";
    }
}