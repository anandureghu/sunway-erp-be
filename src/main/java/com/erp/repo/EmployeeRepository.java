package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO employee_no_seq VALUES (NULL)", nativeQuery = true)
    void incrementEmployeeNo();

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long getCurrentEmployeeNo();

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