package com.erp.repo.appraisal;

import com.erp.domain.appraisal.AppraisalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppraisalConfigRepository extends JpaRepository<AppraisalConfig, Long> {

    Optional<AppraisalConfig> findByYear(Integer year);

    Optional<AppraisalConfig> findByStatus(String status);

    Optional<AppraisalConfig> findByYearAndStatus(Integer year, String status);
}