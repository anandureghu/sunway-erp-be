package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.enums.BenefitType;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.salary.Payroll;
import com.erp.dto.salary.PayrollBatchResponseDTO;
import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.dto.salary.PayrollHistoryDTO;
import com.erp.exception.PayrollGenerationException;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.service.hr.CompanyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeLoanRepository loanRepo;
    private final EmployeeBankDetailsRepository bankRepo;
    private final PayrollRepository payrollRepo;
    private final CompanyService companyService;

    @Transactional
    public Payroll generatePayroll(Long employeeId, PayrollGenerateRequestDTO dto) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new PayrollGenerationException("Employee not found"));

        assertPayDatePresent(dto);
        YearMonth ym = YearMonth.from(dto.getPayDate());
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        if (payrollRepo.existsByEmployee_IdAndPayDateBetween(employeeId, monthStart, monthEnd)) {
            throw new PayrollGenerationException(
                    "Payroll for " + ym + " already exists for " + formatEmployee(employee) + ".",
                    List.of(
                            "Each employee can only have one payroll per calendar month (based on pay date). "
                                    + "Use payroll history or contact HR if this is a mistake."));
        }

        List<String> readiness = new ArrayList<>();
        collectReadinessErrors(employee, readiness);
        if (!readiness.isEmpty()) {
            throw new PayrollGenerationException(
                    "Cannot generate payroll for " + formatEmployee(employee) + ".",
                    readiness);
        }

        return persistPayrollForEmployee(employee, dto);
    }

    /**
     * Generates payroll for every {@link EmployeeStatus#ACTIVE} employee in the company in a single transaction.
     * Validates all employees first; if anyone is missing required data or already has payroll for the pay month,
     * nothing is persisted and {@link PayrollGenerationException} lists every issue.
     */
    @Transactional
    public PayrollBatchResponseDTO generatePayrollBatch(Long companyId, PayrollGenerateRequestDTO dto) {
        companyService.getCompanyById(companyId);
        assertPayDatePresent(dto);

        YearMonth ym = YearMonth.from(dto.getPayDate());
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Employee> active = employeeRepo.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                .sorted(Comparator
                        .comparing(Employee::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getId))
                .toList();

        if (active.isEmpty()) {
            throw new PayrollGenerationException(
                    "No active employees found for this company.",
                    List.of());
        }

        List<String> blocking = new ArrayList<>();

        for (Employee e : active) {
            if (payrollRepo.existsByEmployee_IdAndPayDateBetween(e.getId(), monthStart, monthEnd)) {
                blocking.add(formatEmployee(e) + ": payroll for " + ym + " already exists (pay date in that month)");
            }
        }

        for (Employee e : active) {
            List<String> row = new ArrayList<>();
            collectReadinessErrors(e, row);
            for (String msg : row) {
                blocking.add(formatEmployee(e) + ": " + msg);
            }
        }

        if (!blocking.isEmpty()) {
            throw new PayrollGenerationException(
                    "Cannot generate payroll for " + ym + ". Fix every issue below; no payroll was saved.",
                    blocking);
        }

        for (Employee e : active) {
            persistPayrollForEmployee(e, dto);
        }

        return PayrollBatchResponseDTO.builder()
                .generatedCount(active.size())
                .payrollMonth(ym.toString())
                .build();
    }

    private static void assertPayDatePresent(PayrollGenerateRequestDTO dto) {
        if (dto.getPayDate() == null) {
            throw new PayrollGenerationException("Pay date is required.", List.of());
        }
    }

    private static String formatEmployee(Employee e) {
        String name = ((e.getFirstName() != null ? e.getFirstName() : "") + " "
                + (e.getLastName() != null ? e.getLastName() : "")).trim();
        if (name.isEmpty()) {
            name = "Employee";
        }
        String no = e.getEmployeeNo() != null ? e.getEmployeeNo() : "?";
        return name + " (" + no + ")";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) {
            return a.trim();
        }
        if (!isBlank(b)) {
            return b.trim();
        }
        return null;
    }

    /**
     * Appends human-readable readiness issues (without employee prefix).
     */
    private void collectReadinessErrors(Employee employee, List<String> errors) {
        Optional<EmployeeCompensation> salaryOpt = compensationRepo.findActiveByEmployee(employee);
        if (salaryOpt.isEmpty()) {
            errors.add("no active salary");
            return;
        }
        EmployeeCompensation salary = salaryOpt.get();

        Optional<EmployeeBankDetails> bankOpt = bankRepo.findByEmployee(employee);
        if (bankOpt.isEmpty()) {
            errors.add("bank details missing");
            return;
        }
        EmployeeBankDetails bank = bankOpt.get();
        if (isBlank(bank.getBankShortName())) {
            errors.add("bank short name is missing (e.g. QIB, CBQ — required for bank payroll file)");
        }
        if (isBlank(firstNonBlank(bank.getAccountNo(), bank.getIban()))) {
            errors.add("account number or IBAN is required");
        }

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");
        double totalLoanDeduction = activeLoans.stream()
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();

        double grossPay = computeGrossPay(salary);
        double netPay = grossPay - totalLoanDeduction;
        if (netPay < 0) {
            errors.add("net pay would be negative with current loan deductions");
        }
    }

    private static double computeGrossPay(EmployeeCompensation salary) {
        double grossPay = salary.getBasicSalary();

        if (salary.getHousingType() == BenefitType.ALLOWANCE) {
            grossPay += salary.getHousingAllowance();
        }

        if (salary.getTransportationType() == BenefitType.ALLOWANCE) {
            grossPay += salary.getTransportationAllowance();
        }

        if (salary.getTravelType() == BenefitType.ALLOWANCE) {
            grossPay += salary.getTravelAllowance();
        }

        grossPay += salary.getOtherAllowance();
        return grossPay;
    }

    private Payroll persistPayrollForEmployee(Employee employee, PayrollGenerateRequestDTO dto) {
        EmployeeCompensation salary = compensationRepo.findActiveByEmployee(employee)
                .orElseThrow(() -> new PayrollGenerationException("No active salary found for " + formatEmployee(employee)));

        List<EmployeeLoan> activeLoans = loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");
        double totalLoanDeduction = activeLoans.stream()
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();

        EmployeeBankDetails bank = bankRepo.findByEmployee(employee)
                .orElseThrow(() -> new PayrollGenerationException("Bank details missing for " + formatEmployee(employee)));

        double grossPay = computeGrossPay(salary);
        double netPay = grossPay - totalLoanDeduction;
        if (netPay < 0) {
            throw new PayrollGenerationException("Net pay cannot be negative for " + formatEmployee(employee));
        }

        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayrollCode("PR-" + employee.getId() + "-" + System.nanoTime());
        payroll.setPayPeriodStart(dto.getPayPeriodStart());
        payroll.setPayPeriodEnd(dto.getPayPeriodEnd());
        payroll.setPayDate(dto.getPayDate());
        payroll.setGrossPay(grossPay);
        payroll.setLoanDeduction(totalLoanDeduction);
        payroll.setDeductions(totalLoanDeduction);
        payroll.setNetPayable(netPay);
        payroll.setBankName(bank.getBankName());
        payroll.setBankAccount(bank.getAccountNo());

        payrollRepo.save(payroll);

        for (EmployeeLoan loan : activeLoans) {
            double newBalance = loan.getBalance() - loan.getMonthlyDeduction();
            loan.setBalance(Math.max(newBalance, 0));
            if (loan.getBalance() == 0) {
                loan.setStatus("CLOSED");
            }
            loanRepo.save(loan);
        }

        return payroll;
    }

    /**
     * Same gross/loan/net rules as {@link #generatePayroll} but does not persist.
     * Used for bank file export when no payroll row exists for the month.
     */
    public Optional<ProjectedPayrollAmounts> computeProjectedAmounts(Employee employee) {
        Optional<EmployeeCompensation> salaryOpt = compensationRepo.findActiveByEmployee(employee);
        if (salaryOpt.isEmpty()) {
            return Optional.empty();
        }
        EmployeeCompensation salary = salaryOpt.get();

        List<EmployeeLoan> activeLoans =
                loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");

        double totalLoanDeduction = activeLoans.stream()
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();

        double grossPay = computeGrossPay(salary);

        double netPay = grossPay - totalLoanDeduction;
        if (netPay < 0) {
            throw new RuntimeException("Net pay cannot be negative");
        }

        return Optional.of(new ProjectedPayrollAmounts(
                grossPay, totalLoanDeduction, totalLoanDeduction, netPay));
    }

    public record ProjectedPayrollAmounts(
            double grossPay,
            double loanDeduction,
            double deductions,
            double netPayable
    ) {}

    public List<PayrollHistoryDTO> getPayrollHistory(Long employeeId) {

        Employee employee =
                employeeRepo.findById(employeeId)
                        .orElseThrow(() -> new RuntimeException("Employee not found"));

        return payrollRepo.findByEmployeeOrderByPayDateDesc(employee)
                .stream()
                .map(p -> {
                    PayrollHistoryDTO historyDto = new PayrollHistoryDTO();
                    historyDto.setPayrollCode(p.getPayrollCode());
                    historyDto.setPayPeriodStart(p.getPayPeriodStart());
                    historyDto.setPayPeriodEnd(p.getPayPeriodEnd());
                    historyDto.setPayDate(p.getPayDate());
                    historyDto.setGrossPay(p.getGrossPay());
                    historyDto.setNetPayable(p.getNetPayable());
                    historyDto.setLoanDeduction(p.getLoanDeduction());
                    historyDto.setTotalDeductions(p.getDeductions());
                    return historyDto;
                })
                .collect(Collectors.toList());
    }
}
