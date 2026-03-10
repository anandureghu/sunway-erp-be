package com.erp.repo;

import com.erp.domain.appraisal.EmployeeAppraisalGoal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeAppraisalGoalRepository
        extends JpaRepository<EmployeeAppraisalGoal, Long> {
    boolean existsByTemplateId(Long id);
}