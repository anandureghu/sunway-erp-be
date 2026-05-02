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
}