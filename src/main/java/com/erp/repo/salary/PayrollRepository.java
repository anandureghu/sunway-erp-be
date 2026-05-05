package com.erp.repo.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository
        extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployeeOrderByPayDateDesc(Employee employee);

    /**
     * Bank file export: payroll is included if payDate falls in the given month (inclusive).
     */
    Optional<Payroll> findTopByEmployeeAndPayDateBetweenOrderByPayDateDesc(
            Employee employee,
            LocalDate start,
            LocalDate end);

    // ── added for PayslipDocumentService ──────────────
    Optional<Payroll> findByEmployeeAndPayrollCode(Employee employee, String payrollCode);

    /** True if this employee already has a payroll whose pay date falls in the inclusive range (e.g. one calendar month). */
    boolean existsByEmployee_IdAndPayDateBetween(Long employeeId, LocalDate startInclusive, LocalDate endInclusive);
}