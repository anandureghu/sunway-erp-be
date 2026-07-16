package com.erp.repo.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.COAType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {
    Optional<ChartOfAccounts> findByAccountCode(String code);

    List<ChartOfAccounts> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /** Sum of current balances across active accounts of a given type (e.g. CASH), for dashboard KPIs. */
    @Query("""
            SELECT COALESCE(SUM(a.balance), 0)
            FROM ChartOfAccounts a
            WHERE a.company.id = :companyId
              AND a.type = :type
              AND a.isActive = true
            """)
    BigDecimal sumBalanceByCompanyAndType(
            @Param("companyId") Long companyId,
            @Param("type") COAType type);
}
