package com.erp.repo.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    List<Payroll> findByEmployeeOrderByPayDateDesc(Employee employee);

    Optional<Payroll> findTopByEmployeeAndPayDateBetweenOrderByPayDateDesc(
            Employee employee,
            LocalDate start,
            LocalDate end
    );

    Optional<Payroll> findByEmployeeAndPayrollCode(Employee employee, String payrollCode);

    boolean existsByEmployeeAndPayPeriodStartAndPayPeriodEnd(
            Employee employee,
            LocalDate payPeriodStart,
            LocalDate payPeriodEnd
    );

    // ======================================================
    //  Finance report aggregations
    // ======================================================

    /**
     * Sum of gross payroll cost in [from, to] for a given company.
     * Joined via Employee.company because Payroll has no direct company link.
     */
    @Query("""
            SELECT COALESCE(SUM(p.grossPay), 0)
            FROM Payroll p
            WHERE p.employee.company.id = :companyId
              AND p.payDate IS NOT NULL
              AND (:from IS NULL OR p.payDate >= :from)
              AND (:to IS NULL OR p.payDate <= :to)
            """)
    Double sumPayrollCostBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
