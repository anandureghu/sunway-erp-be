package com.erp.repo.salary;

import com.erp.domain.salary.BenefitGrantType;
import com.erp.domain.salary.EmployeeBenefitGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeBenefitGrantRepository extends JpaRepository<EmployeeBenefitGrant, Long> {

    List<EmployeeBenefitGrant> findByCompanyIdOrderByPayMonthDescIdDesc(Long companyId);

    /** Grants of one type for an employee within a pay-month window (annual-ticket check). */
    List<EmployeeBenefitGrant> findByEmployee_IdAndBenefitTypeAndPayMonthBetween(
            Long employeeId, BenefitGrantType benefitType, LocalDate from, LocalDate to);

    /** Grants a payroll run should pay: by status, for pay months inside the period. */
    List<EmployeeBenefitGrant> findByEmployee_IdAndStatusAndPayMonthBetween(
            Long employeeId, String status, LocalDate from, LocalDate to);

    List<EmployeeBenefitGrant> findByPayrollId(Long payrollId);
}
