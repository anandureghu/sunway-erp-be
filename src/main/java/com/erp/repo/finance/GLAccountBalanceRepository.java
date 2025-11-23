package com.erp.repo.finance;

import com.erp.domain.finance.GLAccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GLAccountBalanceRepository extends JpaRepository<GLAccountBalance, Long> {
    Optional<GLAccountBalance> findByAccountIdAndFiscalYear(Long accountId, String fiscalYear);
}
