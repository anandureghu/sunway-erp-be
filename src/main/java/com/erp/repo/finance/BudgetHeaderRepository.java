package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetHeaderRepository extends JpaRepository<BudgetHeader, Long> {
    List<BudgetHeader> findByCompanyId(Long companyId);

    @Modifying
    @Query("""
                update BudgetHeader bh
                set bh.amount = :amount,
                    bh.updatedAt = CURRENT_TIMESTAMP
                where bh.id = :id
            """)
    void updateAmountOnly(@Param("id") Long id,
                          @Param("amount") Long amount);
}
