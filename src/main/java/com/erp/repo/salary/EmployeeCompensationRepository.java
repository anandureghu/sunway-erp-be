package com.erp.repo.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.EmployeeCompensation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeCompensationRepository
        extends JpaRepository<EmployeeCompensation, Long> {

    @Query("""
        select c from EmployeeCompensation c
        where c.employee = :employee
        and c.status = 'ACTIVE'
        and c.effectiveFrom <= current_date
        and (c.effectiveTo is null or c.effectiveTo >= current_date)
    """)
    Optional<EmployeeCompensation> findActiveByEmployee(
            @Param("employee") Employee employee
    );

    Optional<EmployeeCompensation> findByEmployeeAndStatus(
            Employee employee,
            String status
    );

    @Query("""
        select c from EmployeeCompensation c
        join fetch c.employee e
        where e.company.id = :companyId
          and c.status = 'ACTIVE'
          and (c.housingFollowsCompanyDefault = true or c.foodFollowsCompanyDefault = true)
    """)
    List<EmployeeCompensation> findActiveFollowingCompanyDefaults(
            @Param("companyId") Long companyId
    );
}
