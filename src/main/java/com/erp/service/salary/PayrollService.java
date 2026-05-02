package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.enums.BenefitType;
import com.erp.domain.salary.Payroll;
import com.erp.domain.salary.EmployeeBankDetails;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.salary.PayrollGenerateRequestDTO;
import com.erp.dto.salary.PayrollHistoryDTO;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.repo.salary.EmployeeBankDetailsRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Transactional
    public Payroll generatePayroll(
            Long employeeId,
            PayrollGenerateRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        /* ================= SALARY ================= */
        EmployeeCompensation salary =
                compensationRepo.findActiveByEmployee(employee)
                        .orElseThrow(() ->
                                new RuntimeException("No active salary found"));

        /* ================= LOANS ================= */
        List<EmployeeLoan> activeLoans =
                loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");

        double totalLoanDeduction = activeLoans.stream()
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();

        /* ================= BANK ================= */
        EmployeeBankDetails bank =
                bankRepo.findByEmployee(employee)
                        .orElseThrow(() ->
                                new RuntimeException("Bank details missing"));

        /* ================= GROSS PAY ================= */
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

        /* ================= NET PAY ================= */
        double netPay = grossPay - totalLoanDeduction;
        if (netPay < 0) {
            throw new RuntimeException("Net pay cannot be negative");
        }

        /* ================= SAVE PAYROLL ================= */
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayrollCode("PR-" + System.currentTimeMillis());
        payroll.setPayPeriodStart(dto.getPayPeriodStart());
        payroll.setPayPeriodEnd(dto.getPayPeriodEnd());
        payroll.setPayDate(dto.getPayDate());
        payroll.setGrossPay(grossPay);
        payroll.setLoanDeduction(totalLoanDeduction);
        payroll.setDeductions(totalLoanDeduction);
        payroll.setNetPayable(netPay);
        payroll.setBankName(bank.getBankName());
        payroll.setBankAccount(bank.getAccountNo());

        Payroll savedPayroll = payrollRepo.save(payroll);

        /* ================= UPDATE LOANS ================= */
        for (EmployeeLoan loan : activeLoans) {
            double newBalance = loan.getBalance() - loan.getMonthlyDeduction();

            loan.setBalance(Math.max(newBalance, 0));

            if (loan.getBalance() == 0) {
                loan.setStatus("CLOSED");
            }

            loanRepo.save(loan);
        }

        return savedPayroll;
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
                    PayrollHistoryDTO dto = new PayrollHistoryDTO();
                    dto.setPayrollCode(p.getPayrollCode());
                    dto.setPayPeriodStart(p.getPayPeriodStart());
                    dto.setPayPeriodEnd(p.getPayPeriodEnd());
                    dto.setPayDate(p.getPayDate());
                    dto.setGrossPay(p.getGrossPay());
                    dto.setNetPayable(p.getNetPayable());
                    dto.setLoanDeduction(p.getLoanDeduction());
                    dto.setTotalDeductions(p.getDeductions());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
