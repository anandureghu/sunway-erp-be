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
    // COMPANY QUERIES (✅ FIXED)
    // ======================================================

    List<Employee> findByCompany_Id(Long companyId);

    Page<Employee> findByCompany_Id(Long companyId, Pageable pageable);

    List<Employee> findByCompany(Company company);

    // ======================================================
    // ADMIN (SPRING SECURITY ROLE - ENUM) ✅ FIXED
    // ======================================================

    Optional<Employee> findFirstByCompany_IdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    List<Employee> findAllByCompany_IdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    boolean existsByCompany_IdAndUserRole(
            Long companyId,
            Role role
    );

    // ======================================================
    // BUSINESS ROLE (companyRole - STRING) ✅ ALREADY CORRECT
    // ======================================================

    List<Employee> findByCompany_IdAndUserCompanyRoleIgnoreCase(
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

    Optional<Employee> findByUser_Id(Long userId);
}