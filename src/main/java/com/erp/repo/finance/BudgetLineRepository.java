package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {
    List<BudgetLine> findByBudgetHeader_Id(Long budgetHeaderId);

    Optional<BudgetLine> findByBudgetHeaderAndAccountAndDepartmentAndProjectId(
            BudgetHeader header,
            ChartOfAccounts account,
            Department department,
            String projectId
    );
}

