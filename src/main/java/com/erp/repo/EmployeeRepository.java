package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // ======================================================
    // MYSQL SEQUENCE TABLE (employee_no_seq)
    // ======================================================

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO employee_no_seq VALUES (NULL)", nativeQuery = true)
    void incrementEmployeeNo();

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long getCurrentEmployeeNo();

    // ======================================================
    // COMPANY QUERIES
    // ======================================================

    List<Employee> findByCompanyId(Long companyId);

    Page<Employee> findByCompanyId(Long companyId, Pageable pageable);

    List<Employee> findByCompany(Company company);

    // ======================================================
    // ADMIN (SPRING SECURITY ROLE - ENUM)
    // ======================================================

    Optional<Employee> findFirstByCompanyIdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    List<Employee> findAllByCompanyIdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    boolean existsByCompanyIdAndUserRole(
            Long companyId,
            Role role
    );

    // ======================================================
    // BUSINESS ROLE (STRING companyRole in User)
    // ======================================================

    /**
     * Find employees by company and business role name (case insensitive)
     * Example: "Manager"
     */
    List<Employee> findByCompanyIdAndUserCompanyRoleIgnoreCase(
            Long companyId,
            String roleName
    );

    // ======================================================
    // DEPARTMENT
    // ======================================================

    List<Employee> findByDepartmentId(Long departmentId);

    // ======================================================
    // USER LINK
    // ======================================================

    Optional<Employee> findByUserId(Long userId);
}