package com.erp.repo.finance;

import com.erp.domain.finance.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {
}

