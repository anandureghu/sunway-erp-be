package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Highest numeric employee number already used within a company (non-numeric
     * values such as "ADMIN" cast to 0 and are ignored). Used to seed the
     * per-company employee-number sequence. Returns 0 when the company has none.
     */
    @Query(value = "SELECT COALESCE(MAX(CASE WHEN employee_no REGEXP '^[0-9]+$' "
            + "THEN CAST(employee_no AS UNSIGNED) END), 0) "
            + "FROM employees WHERE company_id = :companyId", nativeQuery = true)
    long findMaxNumericEmployeeNo(@Param("companyId") Long companyId);

    List<Employee> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

    Page<Employee> findByCompany_Id(Long companyId, Pageable pageable);

    List<Employee> findByCompanyOrderByCreatedAtDesc(Company company);

    Optional<Employee> findFirstByCompany_IdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    List<Employee> findAllByCompany_IdAndUserRoleInOrderByCreatedAtDesc(
            Long companyId,
            List<Role> roles
    );

    boolean existsByCompany_IdAndUserRole(
            Long companyId,
            Role role
    );

    List<Employee> findByCompany_IdAndUser_CompanyRoleRef_NameIgnoreCaseOrderByCreatedAtDesc(
            Long companyId,
            String roleName
    );

    List<Employee> findByDepartment_IdOrderByCreatedAtDesc(Long departmentId);

    Optional<Employee> findByUser_Id(Long userId);

    List<Employee> findAllByUser_Id(Long userId);

    Optional<Employee> findByUser_IdAndCompany_Id(Long userId, Long companyId);

    boolean existsByUser_IdAndCompany_Id(Long userId, Long companyId);

    List<Employee> findByCompany_IdAndStatus(Long companyId, EmployeeStatus employeeStatus);
}