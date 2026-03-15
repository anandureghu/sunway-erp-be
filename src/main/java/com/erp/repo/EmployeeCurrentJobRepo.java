package com.erp.repo;

import com.erp.domain.EmployeeCurrentJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeCurrentJobRepo extends JpaRepository<EmployeeCurrentJob, Long> {

    // ✅ Loads employee, jobCode, department in ONE query — no lazy load issues
    @EntityGraph(attributePaths = {"employee", "jobCode", "department"})
    Optional<EmployeeCurrentJob> findByEmployee_Id(Long employeeId);

    boolean existsByEmployee_Id(Long employeeId);
    boolean existsByDepartment_Id(Long departmentId);
}