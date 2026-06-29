package com.erp.repo.sales;

import com.erp.domain.sales.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT COALESCE(SUM(soi.quantity), 0)
            FROM SalesOrder so
            JOIN so.items soi
            WHERE so.company.id = :companyId
              AND so.archived = false
              AND so.status = 'CONFIRMED'
            """)
    Long sumConfirmedOrderQuantity(@Param("companyId") Long companyId);
}
