package com.erp.repo;

import com.erp.domain.EmployeeEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeEducationRepo extends JpaRepository<EmployeeEducation, Long> {

    List<EmployeeEducation> findByEmployeeId(Long employeeId);

    Optional<EmployeeEducation> findByIdAndEmployeeId(Long id, Long employeeId);
}
