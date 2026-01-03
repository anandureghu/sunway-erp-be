package com.erp.repo;

import com.erp.domain.EmployeeCurrentJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeCurrentJobRepo extends JpaRepository<EmployeeCurrentJob, Long> {

    Optional<EmployeeCurrentJob> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
