package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByCompanyId(Long companyId);

    Page<Employee> findByCompanyId(Long companyId, Pageable pageable);

    Optional<Employee> findByCompanyIdAndUserRoleIn(Long companyId, List<Role> roles);

    boolean existsByCompanyIdAndUserRole(Long companyId, Role role);

    List<Employee> findByDepartmentId(Long departmentId);

    Optional<Employee> findByUserId(Long userId);
}
