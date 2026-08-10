package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {

    List<EmployeeLoan> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    List<EmployeeLoan> findByEmployeeIdAndStatus(Long employeeId, String status);

    Optional<EmployeeLoan> findByLoanCode(String loanCode);

    List<EmployeeLoan> findByEmployee(Employee employee);

    List<EmployeeLoan> findByEmployeeId(Long employeeId);

    List<EmployeeLoan> findByEmployeeAndStatus(Employee employee, String active);

    /** True if the employee has any loan in one of the given statuses. */
    boolean existsByEmployeeIdAndStatusIn(Long employeeId, List<String> statuses);

    /** True if a loan code is already used within a company (uniqueness guard). */
    boolean existsByCompany_IdAndLoanCode(Long companyId, String loanCode);

    @Query("""
        select l from EmployeeLoan l
        where l.employee.company.id = :companyId
          and l.status = :status
        order by l.startDate desc
    """)
    List<EmployeeLoan> findByCompanyAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") String status
    );

    /** All loans in a company whose status is one of the given set (for reports). */
    @Query("""
        select l from EmployeeLoan l
        where l.employee.company.id = :companyId
          and l.status in :statuses
        order by l.startDate desc
    """)
    List<EmployeeLoan> findByCompanyAndStatusIn(
            @Param("companyId") Long companyId,
            @Param("statuses") List<String> statuses
    );
}