package com.erp.repo;

import com.erp.domain.appraisal.EmployeeAppraisal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeAppraisalRepository extends JpaRepository<EmployeeAppraisal, Long> {

    Page<EmployeeAppraisal> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<EmployeeAppraisal> findByYear(Integer year, Pageable pageable);

    Optional<EmployeeAppraisal> findByIdAndEmployeeId(Long id, Long employeeId);

    // New: month+year uniqueness check
    boolean existsByEmployeeIdAndYearAndMonth(Long employeeId, Integer year, String month);

    // Keep old one for backward compat if used elsewhere
    boolean existsByEmployeeIdAndYear(Long employeeId, Integer year);
}