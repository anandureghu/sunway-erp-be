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

    private final EmployeeRepository             employeeRepo;
    private final PayrollRepository              payrollRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeLoanRepository         loanRepo;
    private final EmployeeBankDetailsRepository  bankRepo;
    private final TemplateEngine                 templateEngine;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    /* ═══════════════════════════════════════════════
       PUBLIC
    ═══════════════════════════════════════════════ */

    public byte[] generatePayslipPdf(Long employeeId, String payrollCode) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Payroll payroll = payrollRepo
                .findByEmployeeAndPayrollCode(employee, payrollCode)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));

        EmployeeCompensation compensation = compensationRepo
                .findActiveByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("No active compensation found"));

        List<EmployeeLoan> activeLoans =
                loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");

        EmployeeBankDetails bank = bankRepo
                .findByEmployee(employee)
                .orElseThrow(() -> new RuntimeException("Bank details not found"));

        PayslipDocumentDTO dto = buildDTO(
                employee, payroll, compensation, activeLoans, bank);

        String html = renderHtml(dto);

        return renderPdf(html);
    }

    /* ═══════════════════════════════════════════════
       BUILD DTO
    ═══════════════════════════════════════════════ */

    private PayslipDocumentDTO buildDTO(
            Employee employee,
            Payroll payroll,
            EmployeeCompensation compensation,
            List<EmployeeLoan> activeLoans,
            EmployeeBankDetails bank) {

        PayslipDocumentDTO dto = new PayslipDocumentDTO();

        // ── Employee ────────────────────────────────
        dto.setEmployeeNo(employee.getEmployeeNo());
        dto.setFirstName(employee.getFirstName());
        dto.setLastName(employee.getLastName());

        // department entity → departmentName string
        dto.setDepartment(
                employee.getDepartment() != null
                        ? employee.getDepartment().getDepartmentName()
                        : "—"
        );

        // designation comes from User role
        dto.setDesignation(employee.getRole() != null ? employee.getRole() : "—");

        dto.setDateOfJoining(employee.getJoinDate());

        // ── Currency ──────────────────────────────── WITH NULL CHECKS
        if (employee.getCompany() != null && employee.getCompany().getCurrency() != null) {
            dto.setCurrencyCode(employee.getCompany().getCurrency().getCurrencyCode());
            dto.setCurrencySymbol(employee.getCompany().getCurrency().getCurrencySymbol());
        } else {
            // Set default values if company or currency is null
            dto.setCurrencyCode("USD");
            dto.setCurrencySymbol("$");
        }

        // ── Payroll ─────────────────────────────────
        dto.setPayrollCode(payroll.getPayrollCode());
        dto.setPayPeriodStart(payroll.getPayPeriodStart());
        dto.setPayPeriodEnd(payroll.getPayPeriodEnd());
        dto.setPayDate(payroll.getPayDate());

        // ── Line Items ──────────────────────────────
        dto.setEarnings(buildEarnings(compensation));
        dto.setDeductions(buildDeductions(activeLoans));

        // ── Totals (from saved Payroll — source of truth)
        dto.setGrossPay(payroll.getGrossPay());
        dto.setTotalDeductions(payroll.getDeductions());
        dto.setNetPayable(payroll.getNetPayable());

        // ── Bank ────────────────────────────────────
        dto.setBankName(bank.getBankName());
        dto.setBankBranch(bank.getBankBranch());
        dto.setAccountNo(bank.getAccountNo());

        return dto;
    }

    /* ═══════════════════════════════════════════════
       EARNINGS
    ═══════════════════════════════════════════════ */

    private List<LineItemDTO> buildEarnings(EmployeeCompensation c) {

        List<LineItemDTO> list = new ArrayList<>();

        list.add(line("Basic Salary", c.getBasicSalary()));

        if (c.getHousingType() == BenefitType.ALLOWANCE
                && c.getHousingAllowance() > 0)
            list.add(line("Housing Allowance", c.getHousingAllowance()));

        if (c.getTransportationType() == BenefitType.ALLOWANCE
                && c.getTransportationAllowance() > 0)
            list.add(line("Transport Allowance", c.getTransportationAllowance()));

        if (c.getTravelType() == BenefitType.ALLOWANCE
                && c.getTravelAllowance() > 0)
            list.add(line("Travel Allowance", c.getTravelAllowance()));

        if (c.getOtherAllowance() > 0)
            list.add(line("Other Allowance", c.getOtherAllowance()));

        return list;
    }

    /* ═══════════════════════════════════════════════
       DEDUCTIONS
    ═══════════════════════════════════════════════ */

    private List<LineItemDTO> buildDeductions(List<EmployeeLoan> loans) {

        List<LineItemDTO> list = new ArrayList<>();

        loans.stream()
                .filter(l -> l.getMonthlyDeduction() > 0)
                .forEach(l -> list.add(
                        line("Loan — " + l.getLoanCode()
                                        + " (" + l.getLoanType().getName() + ")",
                                l.getMonthlyDeduction())
                ));

        return list;
    }


    private String esc(String value) {
        return value != null ? HtmlUtils.htmlEscape(value) : "—";
    }

    /* ═══════════════════════════════════════════════
       RENDER HTML
    ═══════════════════════════════════════════════ */

    private String renderHtml(PayslipDocumentDTO dto) {

        Context ctx = new Context();

        // Employee
        ctx.setVariable("employeeNo",    esc(dto.getEmployeeNo()));
        ctx.setVariable("firstName",     esc(dto.getFirstName()));
        ctx.setVariable("lastName",      esc(dto.getLastName()));
        ctx.setVariable("department",    esc(dto.getDepartment()));
        ctx.setVariable("designation",   esc(dto.getDesignation()));
        ctx.setVariable("dateOfJoining", formatDate(dto.getDateOfJoining()));
        ctx.setVariable("workingDays",   dto.getWorkingDays());
        ctx.setVariable("totalDays",     dto.getTotalDays());
        ctx.setVariable("leaveTaken",    dto.getLeaveTaken());

        // Currency
        ctx.setVariable("currencyCode",   esc(dto.getCurrencyCode()));
        ctx.setVariable("currencySymbol", esc(dto.getCurrencySymbol()));

        // Payroll
        ctx.setVariable("payrollCode", esc(dto.getPayrollCode()));
        ctx.setVariable("payPeriod",   esc(formatPayPeriod(dto.getPayPeriodStart())));
        ctx.setVariable("payDate",     esc(formatDate(dto.getPayDate())));

        // Line items — escape labels too
        ctx.setVariable("earnings",   escapeLineItems(dto.getEarnings()));
        ctx.setVariable("deductions", escapeLineItems(dto.getDeductions()));

        // Totals — numbers, no escaping needed
        ctx.setVariable("grossPay",        dto.getGrossPay());
        ctx.setVariable("totalDeductions", dto.getTotalDeductions());
        ctx.setVariable("netPayable",      dto.getNetPayable());

        // Bank
        ctx.setVariable("bankName",      esc(dto.getBankName()));
        ctx.setVariable("bankBranch",    esc(dto.getBankBranch()));
        ctx.setVariable("maskedAccount", esc(maskAccount(dto.getAccountNo())));

        // Meta
        ctx.setVariable("generatedAt", LocalDate.now().format(DATE_FMT));

        return templateEngine.process("payslip", ctx);
    }

    /* ═══════════════════════════════════════════════
       RENDER PDF
    ═══════════════════════════════════════════════ */

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

    /* ═══════════════════════════════════════════════
       HELPERS
    ═══════════════════════════════════════════════ */

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
        return date.getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + date.getYear();
    }

    private String maskAccount(String accountNo) {
        if (accountNo == null) return "—";
        String clean = accountNo.replaceAll("[-\\s]", "");
        if (clean.length() < 8) return clean;
        return clean.substring(0, 4) + " •••• "
                + clean.substring(clean.length() - 4);
    }
}