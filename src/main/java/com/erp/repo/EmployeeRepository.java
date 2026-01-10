package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.Role;
import com.erp.domain.hr.Company;
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
    // STANDARD QUERIES
    // ======================================================

    List<Employee> findByCompanyId(Long companyId);

    Page<Employee> findByCompanyId(Long companyId, Pageable pageable);

    Optional<Employee> findByCompanyIdAndUserRoleIn(Long companyId, List<Role> roles);

    boolean existsByCompanyIdAndUserRole(Long companyId, Role role);

    List<Employee> findByDepartmentId(Long departmentId);

    Optional<Employee> findByUserId(Long userId);

    List<Employee> findByCompany(Company company);
}
