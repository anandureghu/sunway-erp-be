package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetHeaderRepository extends JpaRepository<BudgetHeader, Long> {
    List<BudgetHeader> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<BudgetHeader> findByCompanyIdAndFiscalYearAndBudgetTypeAndIsActiveTrueAndProjectIdIsNull(
            Long companyId, String fiscalYear, BudgetType budgetType);

    Optional<BudgetHeader> findByCompanyIdAndFiscalYearAndBudgetTypeAndProjectIdAndIsActiveTrue(
            Long companyId, String fiscalYear, BudgetType budgetType, String projectId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update BudgetHeader bh
            set bh.isActive = false
            where bh.company.id = :companyId
              and bh.fiscalYear = :fiscalYear
              and bh.budgetType = :budgetType
              and bh.isActive = true
              and bh.id <> :exceptId
              and (
                (:projectId is not null and bh.projectId = :projectId)
                or (:projectId is null and bh.projectId is null)
              )
            """)
    int deactivateOtherActivesForScope(
            @Param("companyId") Long companyId,
            @Param("fiscalYear") String fiscalYear,
            @Param("budgetType") BudgetType budgetType,
            @Param("projectId") String projectId,
            @Param("exceptId") Long exceptId);

    long countByParentBudgetId(Long parentId);
}
