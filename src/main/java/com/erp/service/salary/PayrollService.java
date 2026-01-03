package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
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

        // 1️⃣ Active Salary
        EmployeeCompensation salary =
                compensationRepo.findActiveByEmployee(employee)
                        .orElseThrow(() ->
                                new RuntimeException("No active salary"));

        // 2️⃣ Active Loans
        List<EmployeeLoan> activeLoans =
                loanRepo.findByEmployeeAndStatus(employee, "ACTIVE");

        double totalLoanDeduction = activeLoans.stream()
                .mapToDouble(EmployeeLoan::getMonthlyDeduction)
                .sum();

        // 3️⃣ Bank Details
        EmployeeBankDetails bank =
                bankRepo.findByEmployee(employee)
                        .orElseThrow(() ->
                                new RuntimeException("Bank details missing"));

        // 4️⃣ Calculate payroll
        double grossPay = salary.getTotalCompensation();
        double netPay = grossPay - totalLoanDeduction;

        // 5️⃣ Save payroll
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

        Payroll saved = payrollRepo.save(payroll);

        // 6️⃣ Reduce loan balances
        for (EmployeeLoan loan : activeLoans) {
            loan.setBalance(
                    loan.getBalance() - loan.getMonthlyDeduction()
            );

            if (loan.getBalance() <= 0) {
                loan.setStatus("CLOSED");
                loan.setBalance(0.0);
            }
        }

        return saved;
    }

    public List<PayrollHistoryDTO> getPayrollHistory(Long employeeId) {

        Employee employee =
                employeeRepo.findById(employeeId).orElseThrow();

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
                    return dto;
                })
                .toList();
    }
}
