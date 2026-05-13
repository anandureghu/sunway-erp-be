package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.LoanSequence;
import com.erp.domain.LoanType;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.repo.EmployeeLoanRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.LoanSequenceRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final EmployeeCompensationRepository compensationRepo;
    private final AuthContext authContext;

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

        validateLoanAgainstSalary(employee, dto.getLoanAmount(), dto.getLoanPeriod());

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
        loan.setStatus("PENDING_APPROVAL");
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

        validateLoanAgainstSalary(employee, dto.getLoanAmount(), dto.getLoanPeriod());

        Double monthlyDeduction = dto.getLoanAmount() / dto.getLoanPeriod();
        LocalDate endDate = dto.getStartDate().plusMonths(dto.getLoanPeriod());

        loan.setLoanType(dto.getLoanType());
        loan.setLoanAmount(dto.getLoanAmount());
        loan.setLoanPeriod(dto.getLoanPeriod());
        loan.setMonthlyDeduction(monthlyDeduction);
        loan.setStartDate(dto.getStartDate());
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

    /* ================= APPROVE / REJECT LOAN ================= */

    @Transactional
    public LoanResponseDTO decideLoan(Long loanId, boolean approve) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!"PENDING_APPROVAL".equals(loan.getStatus())) {
            throw new RuntimeException(
                    "Loan is not pending approval (current status: " + loan.getStatus() + ")");
        }

        loan.setStatus(approve ? "ACTIVE" : "REJECTED");
        loan = loanRepo.save(loan);

        return toDTO(loan);
    }

    /* ================= PENDING LOANS FOR CURRENT COMPANY ================= */

    /**
     * Returns every PENDING_APPROVAL loan in the caller's company. The
     * controller layer already gates this on LOANS.APPROVE; this method
     * resolves the company from the authenticated user so we never leak
     * loans from a different tenant even if the gate were bypassed.
     */
    public List<LoanResponseDTO> getPendingLoanApprovalsForCurrentCompany() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        Employee approver = employeeRepo.findByUser_Id(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Approver is not linked to an employee record"));

        Long approverCompanyId = approver.getCompany() != null
                ? approver.getCompany().getId() : null;
        if (approverCompanyId == null) {
            throw new AccessDeniedException("Approver is not linked to a company");
        }

        return loanRepo.findByCompanyAndStatus(approverCompanyId, "PENDING_APPROVAL")
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /* ================= MAKE PAYMENT ================= */

    @Transactional
    public LoanResponseDTO makePayment(Long loanId, Double amount) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getStatus().equals("ACTIVE")) {
            throw new RuntimeException("Loan is not active");
        }

        if (amount <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
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

    /* ================= VALIDATION METHOD ================= */

    private void validateLoanAgainstSalary(Employee employee,
                                           Double loanAmount,
                                           Integer loanPeriod) {

        if (loanAmount == null || loanPeriod == null || loanPeriod <= 0) {
            throw new RuntimeException("Invalid loan amount or period");
        }

        EmployeeCompensation compensation = compensationRepo
                .findActiveByEmployee(employee)
                .orElseThrow(() ->
                        new RuntimeException("Active employee compensation not configured"));

        Double salary = compensation.getBasicSalary(); // 🔥 using basic salary

        if (salary == null || salary <= 0) {
            throw new RuntimeException("Employee basic salary not configured");
        }

        Double monthlyDeduction = loanAmount / loanPeriod;

        Double maxAllowed = salary * 0.30; // 30% rule

        if (monthlyDeduction > maxAllowed) {
            throw new RuntimeException(
                    "You don't qualify for this loan amount. Monthly deduction cannot exceed 30% of basic salary (max allowed: "
                            + String.format("%.2f", maxAllowed) + ")."
            );
        }
    }

    /* ================= DTO MAPPER ================= */

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

            if (employee.getCompany() != null &&
                    employee.getCompany().getCurrency() != null) {

                dto.setCurrencyCode(
                        employee.getCompany().getCurrency().getCurrencyCode());

                dto.setCurrencySymbol(
                        employee.getCompany().getCurrency().getCurrencySymbol());
            }
        }

        return dto;
    }
}