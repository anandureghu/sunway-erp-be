package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByCompanyId(Long companyId);

    // find by nested user.role
    Optional<Employee> findByCompanyIdAndUserRole(Long companyId, Role role);

    boolean existsByCompanyIdAndUserRole(Long companyId, Role role);

    List<Employee> findByDepartmentId(Long departmentId);

    Optional<Employee> findByUserId(Long userId);
}
