package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.LoanSequence;
import com.erp.domain.LoanType;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.LoanSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeLoanService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeLoanRepository loanRepo;
    private final LoanSequenceRepository sequenceRepo;

    /* ================= GENERATE LOAN CODE ================= */
    @Transactional
    public String generateLoanCode(LoanType loanType) {

        LoanSequence sequence = sequenceRepo.findByLoanTypeForUpdate(loanType)
                .orElseGet(() -> {
                    LoanSequence newSeq = new LoanSequence();
                    newSeq.setLoanType(loanType);
                    newSeq.setCurrentSequence(0L);
                    return sequenceRepo.save(newSeq);
                });

        Long nextNumber = sequence.getCurrentSequence() + 1;
        sequence.setCurrentSequence(nextNumber);
        sequenceRepo.save(sequence);

        return String.format("%s-%04d", loanType.getPrefix(), nextNumber);
    }

    /* ================= APPLY LOAN ================= */
    @Transactional
    public LoanResponseDTO applyLoan(Long employeeId, LoanRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        String loanCode = generateLoanCode(dto.getLoanType());

        Double monthlyDeduction = dto.getLoanAmount() / dto.getLoanPeriod();
        LocalDate endDate = dto.getStartDate().plusMonths(dto.getLoanPeriod());

        EmployeeLoan loan = new EmployeeLoan();
        loan.setEmployee(employee);
        loan.setLoanCode(loanCode);
        loan.setLoanType(dto.getLoanType());
        loan.setLoanAmount(dto.getLoanAmount());
        loan.setLoanPeriod(dto.getLoanPeriod());
        loan.setMonthlyDeduction(monthlyDeduction);
        loan.setBalance(dto.getLoanAmount());
        loan.setStartDate(dto.getStartDate());
        loan.setEndDate(endDate);
        loan.setStatus("ACTIVE");
        loan.setNotes(dto.getNotes());
        loan = loanRepo.save(loan);
        return toDTO(loan);
    }

    /* ================= UPDATE LOAN ================= */
    @Transactional
    public LoanResponseDTO updateLoan(Long employeeId, Long loanId, LoanRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("Loan does not belong to this employee");
        }

        loan.setLoanType(dto.getLoanType());
        loan.setLoanAmount(dto.getLoanAmount());
        loan.setLoanPeriod(dto.getLoanPeriod());
        loan.setStartDate(dto.getStartDate());

        Double monthlyDeduction = dto.getLoanAmount() / dto.getLoanPeriod();
        loan.setMonthlyDeduction(monthlyDeduction);

        LocalDate endDate = dto.getStartDate().plusMonths(dto.getLoanPeriod());
        loan.setEndDate(endDate);
        loan.setNotes(dto.getNotes());
        loan = loanRepo.save(loan);
        return toDTO(loan);
    }

    /* ================= GET EMPLOYEE LOANS ================= */
    public List<LoanResponseDTO> getEmployeeLoans(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return loanRepo.findByEmployee(employee)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<LoanResponseDTO> getLoansByEmployee(Long employeeId) {
        return getEmployeeLoans(employeeId);
    }

    /* ================= GET LOAN BY ID ================= */
    public LoanResponseDTO getLoanById(Long loanId) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        return toDTO(loan);
    }

    /* ================= DELETE LOAN ================= */
    @Transactional
    public void deleteLoan(Long employeeId, Long loanId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("Loan does not belong to this employee");
        }

        loanRepo.delete(loan);
    }

    /* ================= MAKE PAYMENT ================= */
    @Transactional
    public LoanResponseDTO makePayment(Long loanId, Double amount) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getStatus().equals("ACTIVE")) {
            throw new RuntimeException("Loan is not active");
        }

        Double newBalance = loan.getBalance() - amount;

        if (newBalance < 0) {
            throw new RuntimeException("Payment amount exceeds balance");
        }

        loan.setBalance(newBalance);

        if (newBalance == 0) {
            loan.setStatus("CLOSED");
        }

        loan = loanRepo.save(loan);
        return toDTO(loan);
    }

    /* ================= MAPPER ================= */
    private LoanResponseDTO toDTO(EmployeeLoan loan) {

        LoanResponseDTO dto = new LoanResponseDTO();
        dto.setId(loan.getId());
        dto.setLoanCode(loan.getLoanCode());
        dto.setLoanType(loan.getLoanType());
        dto.setLoanAmount(loan.getLoanAmount());
        dto.setLoanPeriod(loan.getLoanPeriod());
        dto.setMonthlyDeduction(loan.getMonthlyDeduction());
        dto.setBalance(loan.getBalance());
        dto.setStatus(loan.getStatus());
        dto.setStartDate(loan.getStartDate());
        dto.setEndDate(loan.getEndDate());
        dto.setNotes(loan.getNotes());

        Employee employee = loan.getEmployee();
        if (employee != null) {
            dto.setEmployeeId(employee.getId());
            dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        }

        return dto;
    }
}
