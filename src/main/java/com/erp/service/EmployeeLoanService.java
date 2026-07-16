package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import com.erp.domain.LoanSequence;
import com.erp.domain.LoanType;
import com.erp.domain.hr.Company;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.loan.LoanRequestDTO;
import com.erp.dto.loan.LoanResponseDTO;
import com.erp.repo.EmployeeCurrentJobRepo;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeLoanService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeLoanRepository loanRepo;
    private final LoanSequenceRepository sequenceRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final AuthContext authContext;

    /* ================= GENERATE LOAN CODE ================= */

    @Transactional
    public String generateLoanCode(Company company, LoanType loanType) {

        LoanSequence sequence = sequenceRepo.findByCompanyIdAndLoanTypeForUpdate(company.getId(), loanType)
                .orElseGet(() -> {
                    LoanSequence newSeq = new LoanSequence();
                    newSeq.setCompany(company);
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

        // One open loan per employee: block a new request while another loan is
        // still pending approval or active (not yet fully repaid).
        if (loanRepo.existsByEmployeeIdAndStatusIn(
                employeeId, List.of("PENDING_APPROVAL", "ACTIVE"))) {
            throw new RuntimeException(
                    "This employee already has a pending or active loan. Only one loan "
                            + "at a time is allowed — wait for the current one to be rejected "
                            + "or fully repaid (closed) before requesting another.");
        }

        assertSameTenant(employee);
        validateLoanPolicy(employee, dto.getLoanPeriod());
        validateLoanAgainstSalary(employee, dto.getLoanAmount(), dto.getLoanPeriod());

        String loanCode = generateLoanCode(employee.getCompany(), dto.getLoanType());

        Double monthlyDeduction = dto.getLoanAmount() / dto.getLoanPeriod();
        LocalDate endDate = dto.getStartDate().plusMonths(dto.getLoanPeriod());

        EmployeeLoan loan = new EmployeeLoan();
        loan.setEmployee(employee);
        loan.setCompany(employee.getCompany());
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
        assertSameTenant(employee);

        validateLoanPolicy(employee, dto.getLoanPeriod());
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
        assertSameTenant(employee);

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
        assertSameTenant(loan.getEmployee());

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
        assertSameTenant(employee);

        loanRepo.delete(loan);
    }

    /* ================= APPROVE / REJECT LOAN ================= */

    @Transactional
    public LoanResponseDTO decideLoan(Long loanId, boolean approve, String rejectionComment) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        assertSameTenant(loan.getEmployee());

        if (!"PENDING_APPROVAL".equals(loan.getStatus())) {
            throw new RuntimeException(
                    "Loan is not pending approval (current status: " + loan.getStatus() + ")");
        }

        loan.setStatus(approve ? "ACTIVE" : "REJECTED");
        loan.setRejectionComment(
                !approve && rejectionComment != null && !rejectionComment.isBlank()
                        ? rejectionComment.trim()
                        : null);
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

        Employee approver = authContext.getCurrentEmployee();
        if (approver == null) {
            approver = employeeRepo.findByUser_Id(userId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Approver is not linked to an employee record"));
        }

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

    /**
     * Company-wide history of decided loans (active, closed, rejected) for the HR
     * Reports "Loan Approvals" view. Scoped to the caller's company.
     */
    public List<LoanResponseDTO> getCompanyLoanApprovals(boolean archived) {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        Employee approver = authContext.getCurrentEmployee();
        if (approver == null) {
            approver = employeeRepo.findByUser_Id(userId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "User is not linked to an employee record"));
        }

        Long companyId = approver.getCompany() != null ? approver.getCompany().getId() : null;
        if (companyId == null) {
            throw new AccessDeniedException("User is not linked to a company");
        }

        return loanRepo.findByCompanyAndStatusIn(
                        companyId, List.of("ACTIVE", "CLOSED", "REJECTED"))
                .stream()
                .filter(loan -> loan.isArchived() == archived)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Archive / unarchive a decided loan so it drops from (or returns to) the active list. */
    @Transactional
    public LoanResponseDTO setLoanArchived(Long loanId, boolean archived) {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }
        Employee approver = authContext.getCurrentEmployee();
        if (approver == null) {
            approver = employeeRepo.findByUser_Id(userId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "User is not linked to an employee record"));
        }
        Long companyId = approver.getCompany() != null ? approver.getCompany().getId() : null;

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        Long loanCompanyId = loan.getEmployee() != null && loan.getEmployee().getCompany() != null
                ? loan.getEmployee().getCompany().getId() : null;
        if (companyId == null || loanCompanyId == null || !companyId.equals(loanCompanyId)) {
            throw new AccessDeniedException("Loan not found");
        }

        if ("PENDING_APPROVAL".equals(loan.getStatus())) {
            throw new RuntimeException("Loans pending approval cannot be archived");
        }

        loan.setArchived(archived);
        loan = loanRepo.save(loan);
        return toDTO(loan);
    }

    /* ================= MAKE PAYMENT ================= */

    @Transactional
    public LoanResponseDTO makePayment(Long loanId, Double amount) {

        EmployeeLoan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        assertSameTenant(loan.getEmployee());

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

    /* ================= TENANT GUARD ================= */

    private void assertSameTenant(Employee employee) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee != null && employee.getCompany() != null
                ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This loan belongs to a different company");
        }
    }

    /* ================= VALIDATION METHOD ================= */

    /**
     * Enforces the company's loan eligibility & repayment policy (when enabled):
     * a minimum service period since join date before a loan may be requested,
     * and a cap on the repayment period in months.
     */
    private void validateLoanPolicy(Employee employee, Integer loanPeriod) {
        Company company = employee.getCompany();
        if (company == null || !company.isLoanPolicyEnabled()) {
            return;
        }

        Integer maxMonths = company.getLoanMaxRepaymentMonths();
        if (maxMonths != null && maxMonths > 0
                && loanPeriod != null && loanPeriod > maxMonths) {
            throw new RuntimeException(
                    "Repayment period cannot exceed " + maxMonths + " months as per company loan policy.");
        }

        Integer minServiceDays = company.getLoanMinServiceDays();
        if (minServiceDays == null || minServiceDays <= 0) {
            return;
        }

        LocalDate joinDate = resolveJoinDate(employee);
        if (joinDate == null) {
            throw new RuntimeException(
                    "Cannot verify loan eligibility — the employee's join date is not set.");
        }

        long daysOfService = ChronoUnit.DAYS.between(joinDate, LocalDate.now());
        if (daysOfService < minServiceDays) {
            long remaining = minServiceDays - daysOfService;
            throw new RuntimeException(
                    "Employee is not yet eligible for a loan. Company policy requires "
                            + minServiceDays + " days of service before requesting a loan ("
                            + Math.max(remaining, 0) + " more day(s) to go).");
        }
    }

    /**
     * Effective join date: employee.joinDate, falling back to the current job's
     * start / effective-from date when only those are set.
     */
    private LocalDate resolveJoinDate(Employee employee) {
        if (employee.getJoinDate() != null) {
            return employee.getJoinDate();
        }
        if (employee.getId() == null) {
            return null;
        }
        return currentJobRepo.findByEmployee_Id(employee.getId())
                .map(job -> job.getStartDate() != null
                        ? job.getStartDate()
                        : job.getEffectiveFrom())
                .orElse(null);
    }

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
        dto.setRejectionComment(loan.getRejectionComment());
        dto.setArchived(loan.isArchived());

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