package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.ResidencePermit;
import com.erp.domain.enums.BenefitType;
import com.erp.domain.hr.Company;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.salary.Payroll;
import com.erp.exception.PayrollExportException;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.ResidencePermitRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.service.hr.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Qatar-style bank payroll CSV (two-row summary header + detail rows).
 * File creation timestamps use {@link #QATAR_ZONE}.
 */
@Service
@RequiredArgsConstructor
public class PayrollBankFileExportService {

    public static final ZoneId QATAR_ZONE = ZoneId.of("Asia/Qatar");

    private static final int SUMMARY_COLS = 21;
    private static final int DETAIL_COLS = 22;
    private static final int WORKING_DAYS = 30;

    private final CompanyService companyService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeCompensationRepository compensationRepository;
    private final EmployeeBankDetailsRepository bankRepository;
    private final PayrollRepository payrollRepository;
    private final ResidencePermitRepository residencePermitRepository;
    private final PayrollService payrollService;

    public String buildBankPayrollCsv(Long companyId, String yearMonthYyyyMm) {
        Company company = companyService.getCompanyById(companyId);
        validateCompanyPayerSettings(company);

        YearMonth ym;
        try {
            ym = YearMonth.parse(yearMonthYyyyMm, DateTimeFormatter.ofPattern("yyyyMM"));
        } catch (Exception e) {
            throw new PayrollExportException("Invalid yearMonth: use format yyyyMM (e.g. 202512)");
        }

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Employee> employees = employeeRepository.findByCompany_IdOrderByCreatedAtDesc(companyId)
                .stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                .sorted(Comparator
                        .comparing(Employee::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getId))
                .toList();

        List<String> validationErrors = new ArrayList<>();
        List<DetailRow> detailRows = new ArrayList<>();

        for (Employee emp : employees) {
            Optional<EmployeeCompensation> compOpt = compensationRepository.findActiveByEmployee(emp);
            if (compOpt.isEmpty()) {
                continue;
            }
            EmployeeCompensation comp = compOpt.get();
            String name = (emp.getFirstName() + " " + emp.getLastName()).trim();

            Optional<EmployeeBankDetails> bankOpt = bankRepository.findByEmployee(emp);
            if (bankOpt.isEmpty()) {
                validationErrors.add(name + ": missing bank details");
                continue;
            }
            EmployeeBankDetails bank = bankOpt.get();

            String qid = trim(emp.getIdentification());
            if (qid == null) {
                validationErrors.add(name + ": missing QID / national ID (identification field)");
                continue;
            }

            String bankShort = trim(bank.getBankShortName());
            if (bankShort == null) {
                validationErrors.add(name + ": missing bank short name (e.g. QIB, CBQ)");
                continue;
            }

            String account = firstNonBlank(bank.getIban(), bank.getAccountNo());
            if (account == null) {
                validationErrors.add(name + ": missing IBAN or account number");
                continue;
            }

            Optional<Payroll> payrollOpt = payrollRepository
                    .findTopByEmployeeAndPayDateBetweenOrderByPayDateDesc(emp, monthStart, monthEnd);

            double net;
            double deductions;

            if (payrollOpt.isPresent()) {
                Payroll p = payrollOpt.get();
                deductions = p.getDeductions() != null ? nz(p.getDeductions()) : nz(p.getLoanDeduction());
                net = nz(p.getNetPayable());
            } else {
                PayrollService.ProjectedPayrollAmounts proj;
                try {
                    Optional<PayrollService.ProjectedPayrollAmounts> po =
                            payrollService.computeProjectedAmounts(emp);
                    if (po.isEmpty()) {
                        validationErrors.add(name + ": could not compute payroll amounts");
                        continue;
                    }
                    proj = po.get();
                } catch (RuntimeException ex) {
                    validationErrors.add(name + ": " + ex.getMessage());
                    continue;
                }
                deductions = proj.deductions();
                net = proj.netPayable();
            }

            String visaId = residencePermitRepository.findByEmployeeId(emp.getId())
                    .map(ResidencePermit::getPermitIdNumber)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("");

            double basic = nz(comp.getBasicSalary());
            double housing = comp.getHousingType() == BenefitType.ALLOWANCE
                    ? nz(comp.getHousingAllowance()) : 0d;
            double food = 0d;
            double transport = comp.getTransportationType() == BenefitType.ALLOWANCE
                    ? nz(comp.getTransportationAllowance()) : 0d;
            double overtime = 0d;
            double travelAllow = comp.getTravelType() == BenefitType.ALLOWANCE
                    ? nz(comp.getTravelAllowance()) : 0d;
            double otherAllow = nz(comp.getOtherAllowance());

            detailRows.add(new DetailRow(
                    qid,
                    visaId,
                    name,
                    bankShort,
                    account,
                    net,
                    basic,
                    housing,
                    food,
                    transport,
                    overtime,
                    deductions,
                    travelAllow,
                    otherAllow
            ));
        }

        if (!validationErrors.isEmpty()) {
            throw new PayrollExportException(
                    "Fix employee data before exporting the bank payroll file.",
                    validationErrors);
        }

        double totalNet = detailRows.stream().mapToDouble(DetailRow::net).sum();
        int recordCount = detailRows.size();

        ZonedDateTime now = ZonedDateTime.now(QATAR_ZONE);
        String fileDate = now.format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileTime = now.format(DateTimeFormatter.ofPattern("HHmm"));

        String employerEid = trim(company.getPayrollEmployerEid());
        String payerEid = trim(company.getPayrollPayerEid());
        String payerQid = trim(company.getPayrollPayerQid());
        if (payerQid == null) {
            payerQid = "";
        }
        String payerBank = trim(company.getPayrollPayerBankShortName());
        String payerIban = trim(company.getPayrollPayerIban());
        String sif = trim(company.getPayrollSifVersion());
        if (sif == null) {
            sif = "1";
        }

        StringBuilder sb = new StringBuilder();

        sb.append(lineSummaryHeader()).append('\n');
        sb.append(lineSummaryValues(
                employerEid, fileDate, fileTime, payerEid, payerQid, payerBank, payerIban,
                yearMonthYyyyMm, totalNet, recordCount, sif)).append('\n');
        sb.append(lineDetailHeader()).append('\n');

        int id = 1;
        for (DetailRow row : detailRows) {
            sb.append(lineDetail(id++, row)).append('\n');
        }

        return sb.toString();
    }

    private void validateCompanyPayerSettings(Company company) {
        List<String> missing = new ArrayList<>();
        if (isBlank(company.getPayrollEmployerEid())) {
            missing.add("Employer EID");
        }
        if (isBlank(company.getPayrollPayerEid())) {
            missing.add("Payer EID");
        }
        if (isBlank(company.getPayrollPayerBankShortName())) {
            missing.add("Payer bank short name");
        }
        if (isBlank(company.getPayrollPayerIban())) {
            missing.add("Payer IBAN");
        }
        if (!missing.isEmpty()) {
            throw new PayrollExportException(
                    "Complete payroll export settings in HR Settings → Payroll (bank file): "
                            + String.join(", ", missing));
        }
    }

    private static String lineSummaryHeader() {
        List<String> cols = new ArrayList<>();
        cols.add("Employer EID");
        cols.add("File Creation Date");
        cols.add("File Creation Time");
        cols.add("Payer EID");
        cols.add("Payer QID");
        cols.add("Payer Bank Short Name");
        cols.add("Payer IBAN");
        cols.add("Salary Year and Month");
        cols.add("Total Salaries");
        cols.add("Total Records");
        cols.add("SIF Version");
        while (cols.size() < SUMMARY_COLS) {
            cols.add("");
        }
        return joinCsv(cols);
    }

    private static String lineSummaryValues(
            String employerEid,
            String fileDate,
            String fileTime,
            String payerEid,
            String payerQid,
            String payerBank,
            String payerIban,
            String salaryYm,
            double totalSalaries,
            int totalRecords,
            String sifVersion
    ) {
        List<String> cols = new ArrayList<>();
        cols.add(employerEid);
        cols.add(fileDate);
        cols.add(fileTime);
        cols.add(payerEid);
        cols.add(payerQid);
        cols.add(payerBank);
        cols.add(payerIban);
        cols.add(salaryYm);
        cols.add(fmtAmount(totalSalaries));
        cols.add(String.valueOf(totalRecords));
        cols.add(sifVersion);
        while (cols.size() < SUMMARY_COLS) {
            cols.add("");
        }
        return joinCsv(cols);
    }

    private static String lineDetailHeader() {
        List<String> cols = new ArrayList<>();
        cols.add("Record ID");
        cols.add("Employee QID");
        cols.add("Employee Visa ID");
        cols.add("Employee Name");
        cols.add("Employee Bank Short Name");
        cols.add("Employee Account");
        cols.add("Salary Frequency");
        cols.add("Number of Working Days");
        cols.add("Net Salary");
        cols.add("Basic Salary");
        cols.add("Extra hours");
        cols.add("Extra Income");
        cols.add("Deductions");
        cols.add("Payment Type");
        cols.add("Notes / Comments");
        cols.add("Housing Allowance");
        cols.add("Food Allowance");
        cols.add("Transportation Allowance");
        cols.add("Over Time Allowance");
        cols.add("Deduction Reason Code");
        cols.add("Extra Field 1");
        cols.add("Extra Field 2");
        return joinCsv(cols);
    }

    private static String lineDetail(int recordId, DetailRow r) {
        List<String> cols = new ArrayList<>();
        cols.add(String.valueOf(recordId));
        cols.add(r.qid);
        cols.add(r.visaId);
        cols.add(r.name);
        cols.add(r.bankShort);
        cols.add(r.account);
        cols.add("M");
        cols.add(String.valueOf(WORKING_DAYS));
        cols.add(fmtAmount(r.net));
        cols.add(fmtAmount(r.basic));
        cols.add("0");
        cols.add("0");
        cols.add(fmtAmount(r.deductions));
        cols.add("Normal");
        cols.add("");
        cols.add(fmtAmount(r.housing));
        cols.add(fmtAmount(r.food));
        cols.add(fmtAmount(r.transport));
        cols.add(fmtAmount(r.overtime));
        cols.add("0");
        cols.add(fmtAmount(r.travelAllow));
        cols.add(fmtAmount(r.otherAllow));
        if (cols.size() != DETAIL_COLS) {
            throw new IllegalStateException("Detail column mismatch: " + cols.size());
        }
        return joinCsv(cols);
    }

    private static String joinCsv(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvEsc(cells.get(i)));
        }
        return sb.toString();
    }

    private static String csvEsc(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\r") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String fmtAmount(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static double nz(Double d) {
        return d == null ? 0d : d;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isBlank(String s) {
        return trim(s) == null;
    }

    private static String firstNonBlank(String a, String b) {
        String ta = trim(a);
        if (ta != null) {
            return ta;
        }
        return trim(b);
    }

    private record DetailRow(
            String qid,
            String visaId,
            String name,
            String bankShort,
            String account,
            double net,
            double basic,
            double housing,
            double food,
            double transport,
            double overtime,
            double deductions,
            /** Travel allowance when ALLOWANCE — mapped to Extra Field 1 */
            double travelAllow,
            /** Other allowance — mapped to Extra Field 2; food column stays 0 (no separate field in HR). */
            double otherAllow
    ) {}
}
