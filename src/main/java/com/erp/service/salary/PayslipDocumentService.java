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
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
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
    private final TemplateEngine templateEngine;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generatePayslipPdf(Long employeeId, String payrollCode) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

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
        dto.setPaidLeaveDays(payroll.getPaidLeaveDays());
        dto.setUnpaidLeaveDays(payroll.getUnpaidLeaveDays());
        dto.setPayableDays(payroll.getPayableDays());
        dto.setLopDays(payroll.getLopDays());
        dto.setLopAmount(payroll.getLopAmount());

        dto.setEarnings(buildEarnings(compensation));
        dto.setDeductions(buildDeductions(activeLoans));

        dto.setGrossPay(payroll.getGrossPay());
        dto.setTotalDeductions(payroll.getDeductions());
        dto.setNetPayable(payroll.getNetPayable());

        dto.setBankName(bank.getBankName());
        dto.setBankBranch(bank.getBankBranch());
        dto.setAccountNo(bank.getAccountNo());

        return dto;
    }

    private List<LineItemDTO> buildEarnings(EmployeeCompensation c) {
        List<LineItemDTO> list = new ArrayList<>();

        list.add(line("Basic Salary", c.getBasicSalary()));

        if (c.getHousingType() == BenefitType.ALLOWANCE && c.getHousingAllowance() > 0) {
            list.add(line("Housing Allowance", c.getHousingAllowance()));
        }

        if (c.getTransportationType() == BenefitType.ALLOWANCE && c.getTransportationAllowance() > 0) {
            list.add(line("Transport Allowance", c.getTransportationAllowance()));
        }

        if (c.getTravelType() == BenefitType.ALLOWANCE && c.getTravelAllowance() > 0) {
            list.add(line("Travel Allowance", c.getTravelAllowance()));
        }

        if (c.getOtherAllowance() > 0) {
            list.add(line("Other Allowance", c.getOtherAllowance()));
        }

        return list;
    }

    private List<LineItemDTO> buildDeductions(List<EmployeeLoan> loans) {
        List<LineItemDTO> list = new ArrayList<>();
        if (loans == null) {
            return list;
        }

        loans.stream()
                .filter(l -> l.getMonthlyDeduction() > 0)
                .forEach(l -> list.add(
                        line("Loan — " + l.getLoanCode() + " (" + l.getLoanType().getName() + ")",
                                l.getMonthlyDeduction())
                ));

        return list;
    }

    private String esc(String value) {
        return value != null ? HtmlUtils.htmlEscape(value) : "—";
    }

    private String renderHtml(PayslipDocumentDTO dto) {
        Context ctx = new Context();

        ctx.setVariable("employeeNo", esc(dto.getEmployeeNo()));
        ctx.setVariable("firstName", esc(dto.getFirstName()));
        ctx.setVariable("lastName", esc(dto.getLastName()));
        ctx.setVariable("department", esc(dto.getDepartment()));
        ctx.setVariable("designation", esc(dto.getDesignation()));
        ctx.setVariable("dateOfJoining", formatDate(dto.getDateOfJoining()));

        ctx.setVariable("workingDays", dto.getWorkingDays());
        ctx.setVariable("totalDays", dto.getTotalDays());
        ctx.setVariable("leaveTaken", dto.getLeaveTaken());
        ctx.setVariable("workedDays", dto.getWorkedDays());
        ctx.setVariable("workedHours", dto.getWorkedHours());
        ctx.setVariable("paidLeaveDays", dto.getPaidLeaveDays());
        ctx.setVariable("unpaidLeaveDays", dto.getUnpaidLeaveDays());
        ctx.setVariable("payableDays", dto.getPayableDays());
        ctx.setVariable("lopDays", dto.getLopDays());
        ctx.setVariable("lopAmount", dto.getLopAmount());

        ctx.setVariable("currencyCode", esc(dto.getCurrencyCode()));
        ctx.setVariable("currencySymbol", esc(dto.getCurrencySymbol()));

        ctx.setVariable("payrollCode", esc(dto.getPayrollCode()));
        ctx.setVariable("payPeriod", esc(formatPayPeriod(dto.getPayPeriodStart())));
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

    private String resolveDesignation(Employee employee) {
        if (employee == null) return "—";

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