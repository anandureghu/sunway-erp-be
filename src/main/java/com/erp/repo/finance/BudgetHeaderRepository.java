package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetHeaderRepository extends JpaRepository<BudgetHeader, Long> {
    List<BudgetHeader> findByCompanyId(Long companyId);

    Optional<BudgetHeader> findByCompanyIdAndFiscalYearAndIsActiveTrue(Long companyId, String fiscalYear);

    @Modifying(clearAutomatically = true)
    @Query("""
            update BudgetHeader bh
            set bh.isActive = false
            where bh.company.id = :companyId
              and bh.fiscalYear = :fiscalYear
              and bh.id <> :exceptId
            """)
    int deactivateOtherActivesForFiscalYear(
            @Param("companyId") Long companyId,
            @Param("fiscalYear") String fiscalYear,
            @Param("exceptId") Long exceptId);

    @Modifying
    @Query("""
                update BudgetHeader bh
                set bh.amount = :amount,
                    bh.updatedAt = CURRENT_TIMESTAMP
                where bh.id = :id
            """)
    void updateAmountOnly(@Param("id") Long id,
                          @Param("amount") Long amount);

    long countByParentBudgetId(Long parentId);
}
