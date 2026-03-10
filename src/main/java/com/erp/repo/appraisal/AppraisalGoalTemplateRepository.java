package com.erp.repo.appraisal;

import com.erp.domain.appraisal.AppraisalGoalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppraisalGoalTemplateRepository
        extends JpaRepository<AppraisalGoalTemplate, Long> {

    List<AppraisalGoalTemplate>
    findByRoleConfigIdAndActiveTrue(Long roleConfigId);
}