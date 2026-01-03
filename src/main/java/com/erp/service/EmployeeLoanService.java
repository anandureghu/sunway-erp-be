package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeLoanRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeLoanService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeLoanRepository loanRepo;

    /* ================= CREATE LOAN ================= */

    @Transactional
    public void createLoan(Long employeeId, LoanRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeLoan loan = new EmployeeLoan();
        loan.setEmployee(employee);
        loan.setLoanCode(dto.getLoanCode());
        loan.setLoanAmount(dto.getLoanAmount());
        loan.setLoanPeriod(dto.getLoanPeriod());
        loan.setMonthlyDeduction(dto.getMonthlyDeduction());
        loan.setBalance(dto.getLoanAmount()); // initial balance
        loan.setStartDate(dto.getStartDate());
        loan.setStatus("ACTIVE");

        loanRepo.save(loan);
    }

    /* ================= UPDATE LOAN ================= */

    @Transactional
    public void updateLoan(Long employeeId, Long loanId, LoanRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        //ensure loan belongs to employee
        if (!loan.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("Loan does not belong to this employee");
        }

        loan.setLoanCode(dto.getLoanCode());
        loan.setLoanAmount(dto.getLoanAmount());
        loan.setLoanPeriod(dto.getLoanPeriod());
        loan.setMonthlyDeduction(dto.getMonthlyDeduction());
        loan.setStartDate(dto.getStartDate());

        loanRepo.save(loan);
    }

    /* ================= GET ALL LOANS ================= */

    public List<LoanResponseDTO> getLoansByEmployee(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return loanRepo.findByEmployee(employee)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /* ================= MAPPER ================= */

    private LoanResponseDTO toDTO(EmployeeLoan loan) {
        LoanResponseDTO dto = new LoanResponseDTO();
        dto.setId(loan.getId());
        dto.setLoanCode(loan.getLoanCode());
        dto.setLoanAmount(loan.getLoanAmount());
        dto.setLoanPeriod(loan.getLoanPeriod());
        dto.setMonthlyDeduction(loan.getMonthlyDeduction());
        dto.setBalance(loan.getBalance());
        dto.setStatus(loan.getStatus());
        dto.setStartDate(loan.getStartDate());
        return dto;
    }
}
