package com.erp.repo;

import com.erp.domain.EmployeeExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
public interface EmployeeExperienceRepo extends JpaRepository<EmployeeExperience, Long> {

    List<EmployeeExperience> findByEmployeeId(Long employeeId);

    Optional<EmployeeExperience> findByIdAndEmployeeId(Long id, Long employeeId);
}

