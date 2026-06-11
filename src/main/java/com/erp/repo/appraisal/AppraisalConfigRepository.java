package com.erp.repo.appraisal;

import com.erp.domain.appraisal.AppraisalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppraisalConfigRepository extends JpaRepository<AppraisalConfig, Long> {

    // ── Single (first-match) lookups — backward-compatible getByYear/getActive ──
    Optional<AppraisalConfig> findFirstByCompany_IdAndYearOrderByIdDesc(Long companyId, Integer year);

    Optional<AppraisalConfig> findFirstByCompanyIsNullAndYearOrderByIdDesc(Integer year);

    Optional<AppraisalConfig> findFirstByStatusAndCompany_IdOrderByYearDesc(String status, Long companyId);

    Optional<AppraisalConfig> findFirstByStatusAndCompanyIsNullOrderByYearDesc(String status);

    // ── List lookups (multiple cycles per year / multiple active) ──────────────
    List<AppraisalConfig> findByCompany_IdAndYearOrderByIdDesc(Long companyId, Integer year);

    List<AppraisalConfig> findByCompanyIsNullAndYearOrderByIdDesc(Integer year);

    List<AppraisalConfig> findByStatusAndCompany_Id(String status, Long companyId);

    List<AppraisalConfig> findByStatusAndCompanyIsNull(String status);

    // ── Uniqueness within a tenant: (year, cycleName) ──────────────────────────
    Optional<AppraisalConfig> findByCompany_IdAndYearAndCycleName(Long companyId, Integer year, String cycleName);

    Optional<AppraisalConfig> findByCompanyIsNullAndYearAndCycleName(Integer year, String cycleName);
}