package com.erp.repo.sales;

import com.erp.domain.sales.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Page<SalesOrder> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<SalesOrder> findByCompanyIdAndArchivedTrue(Long companyId);

    long countByCompanyIdAndArchivedFalseAndStatus(Long companyId, String status);

    @Query("""
            SELECT DISTINCT so FROM SalesOrder so
            LEFT JOIN so.customer c
            LEFT JOIN com.erp.domain.finance.Invoice inv
              ON inv.orderId = so.id
             AND inv.type = com.erp.domain.InvoiceType.SALES
             AND inv.company.id = so.company.id
            WHERE so.company.id = :companyId
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(so.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.customerName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(so.status) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                :tab IS NULL OR :tab = '' OR :tab = 'all'
                OR (:tab = 'active' AND so.status NOT IN ('COMPLETED', 'CANCELLED'))
                OR (:tab = 'closed' AND so.status IN ('COMPLETED', 'CANCELLED') AND so.archived = false)
              )
              AND (
                :status IS NULL OR :status = '' OR :status = 'all'
                OR (:status = 'NOT_QUOTATION' AND UPPER(so.status) <> 'QUOTATION')
                OR (:status <> 'NOT_QUOTATION' AND UPPER(so.status) = UPPER(:status))
              )
              AND (
                :paymentStatus IS NULL OR :paymentStatus = '' OR :paymentStatus = 'all'
                OR UPPER(COALESCE(inv.status, 'UNPAID')) = UPPER(:paymentStatus)
              )
              AND (:dateFrom IS NULL OR so.orderDate >= :dateFrom)
              AND (:dateTo IS NULL OR so.orderDate <= :dateTo)
            """)
    Page<SalesOrder> search(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("tab") String tab,
            @Param("status") String status,
            @Param("paymentStatus") String paymentStatus,
            @Param("dateFrom") java.time.LocalDate dateFrom,
            @Param("dateTo") java.time.LocalDate dateTo,
            Pageable pageable
    );

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
