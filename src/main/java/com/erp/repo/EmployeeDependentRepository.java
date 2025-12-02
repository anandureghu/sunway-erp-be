package com.erp.repo;

import com.erp.domain.EmployeeDependent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDependentRepository extends JpaRepository<EmployeeDependent, Long> {
    List<EmployeeDependent> findByEmployeeId(Long employeeId);
}
