package com.erp.repo.finance;

import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.BudgetStatus;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {
    List<BudgetLine> findByBudgetHeader_IdOrderByCreatedAtDesc(Long budgetHeaderId);

    Optional<BudgetLine> findByBudgetHeaderAndAccountAndDepartmentAndProjectId(
            BudgetHeader header,
            ChartOfAccounts account,
            Department department,
            String projectId
    );

    @Query("""
            SELECT bl.department.id,
                   bl.department.departmentName,
                   bl.department.departmentCode,
                   COALESCE(SUM(bl.amount), 0)
            FROM BudgetLine bl
            JOIN bl.budgetHeader bh
            WHERE bh.company.id = :companyId
              AND bh.isActive = true
              AND bh.status = :status
              AND bl.department IS NOT NULL
              AND bl.startDate <= :to
              AND bl.endDate >= :from
            GROUP BY bl.department.id, bl.department.departmentName, bl.department.departmentCode
            ORDER BY bl.department.departmentName
            """)
    List<Object[]> sumBudgetedByDepartment(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") BudgetStatus status);
}

