package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.hr.RetirementCompensationDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Computes end-of-service compensation for an employee using the company's
 * configurable policy (basic salary * monthsPerYear per completed year of
 * service). Read-only — actual disbursement is handled by payroll.
 */
@Service
@RequiredArgsConstructor
public class RetirementCompensationService {

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final AuthContext authContext;

    public RetirementCompensationDTO calculate(Long employeeId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        Company company = employee.getCompany();
        if (company == null) {
            throw new NotFoundException("Employee company not set");
        }

        Long currentCompanyId = authContext.getCurrentCompanyId();
        String role = authContext.getCurrentUserRole();
        boolean isSuperAdmin = role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
        if (!isSuperAdmin && (currentCompanyId == null || !currentCompanyId.equals(company.getId()))) {
            throw new AccessDeniedException("Access denied for this employee");
        }

        if (!company.isRetirementCompensationEnabled()) {
            throw new IllegalStateException(
                    "Retirement compensation is not enabled for this company");
        }

        LocalDate joinDate = resolveJoinDate(employee);
        if (joinDate == null) {
            throw new IllegalStateException(
                    "Cannot compute retirement compensation — set the employee's join date "
                            + "or the Current Job's start / effective date");
        }

        EmployeeCompensation comp = compensationRepo.findActiveByEmployee(employee)
                .orElseThrow(() -> new NotFoundException(
                        "Active compensation record not found for this employee"));

        BigDecimal basicSalary = comp.getBasicSalary() != null
                ? BigDecimal.valueOf(comp.getBasicSalary())
                : BigDecimal.ZERO;

        BigDecimal monthsPerYear = company.getRetirementCompensationMonthsPerYear() != null
                ? company.getRetirementCompensationMonthsPerYear()
                : BigDecimal.ONE;

        LocalDate today = LocalDate.now();
        long daysOfService = Math.max(ChronoUnit.DAYS.between(joinDate, today), 0);

        BigDecimal yearsOfService = BigDecimal.valueOf(daysOfService)
                .divide(DAYS_PER_YEAR, 4, RoundingMode.HALF_UP);

        BigDecimal accruedMonths = yearsOfService.multiply(monthsPerYear)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal compensationAmount = basicSalary.multiply(accruedMonths)
                .setScale(2, RoundingMode.HALF_UP);

        String currencyCode = company.getCurrency() != null
                ? company.getCurrency().getCurrencyCode()
                : null;

        String name = (safe(employee.getFirstName()) + " " + safe(employee.getLastName())).trim();

        return RetirementCompensationDTO.builder()
                .employeeId(employee.getId())
                .employeeName(name.isEmpty() ? null : name)
                .joinDate(joinDate)
                .calculatedOn(today)
                .yearsOfService(yearsOfService)
                .basicSalary(basicSalary)
                .monthsPerYear(monthsPerYear)
                .accruedMonths(accruedMonths)
                .compensationAmount(compensationAmount)
                .currencyCode(currencyCode)
                .build();
    }

    /**
     * Accrued end-of-service gratuity for an employee, for internal use by payroll
     * during a final settlement. Unlike {@link #calculate(Long)} this performs no
     * tenant/permission checks and never throws — it returns {@link BigDecimal#ZERO}
     * when the policy is disabled or the data needed to compute it is missing.
     */
    public BigDecimal computeAccruedAmount(Employee employee) {
        if (employee == null) {
            return BigDecimal.ZERO;
        }

        Company company = employee.getCompany();
        if (company == null || !company.isRetirementCompensationEnabled()) {
            return BigDecimal.ZERO;
        }

        LocalDate joinDate = resolveJoinDate(employee);
        if (joinDate == null) {
            return BigDecimal.ZERO;
        }

        EmployeeCompensation comp = compensationRepo.findActiveByEmployee(employee).orElse(null);
        if (comp == null || comp.getBasicSalary() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basicSalary = BigDecimal.valueOf(comp.getBasicSalary());
        BigDecimal monthsPerYear = company.getRetirementCompensationMonthsPerYear() != null
                ? company.getRetirementCompensationMonthsPerYear()
                : BigDecimal.ONE;

        long daysOfService = Math.max(ChronoUnit.DAYS.between(joinDate, LocalDate.now()), 0);
        BigDecimal yearsOfService = BigDecimal.valueOf(daysOfService)
                .divide(DAYS_PER_YEAR, 4, RoundingMode.HALF_UP);
        BigDecimal accruedMonths = yearsOfService.multiply(monthsPerYear)
                .setScale(4, RoundingMode.HALF_UP);

        return basicSalary.multiply(accruedMonths).setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Effective join date: employee.joinDate, with fallbacks to the current
     * job's start / effective-from when HR has only filled in those fields.
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
}
