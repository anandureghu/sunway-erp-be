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
    List<Employee> findByDepartmentId(Long departmentId);
    boolean existsByCompanyIdAndRole(Long companyId, Role role);
    Optional<Employee> findByCompany_IdAndRole(Long companyId, Role role);
}
