package com.erp.repo.purchase;

import com.erp.domain.purchase.GoodsReceipt;
import com.erp.domain.purchase.GoodsReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    List<GoodsReceipt> findByPurchaseOrderId(Long purchaseOrderId);

    List<GoodsReceipt> findByCompany_IdOrderByReceivedAtDesc(Long companyId);

    List<GoodsReceipt> findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(Long companyId);

    Page<GoodsReceipt> findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(Long companyId, Pageable pageable);

    List<GoodsReceipt> findByCompany_IdAndStatusAndArchivedFalseOrderByReceivedAtDesc(
            Long companyId, GoodsReceiptStatus status);

    long countByCompany_IdAndStatusAndArchivedFalse(Long companyId, GoodsReceiptStatus status);

    @Query("""
            SELECT COUNT(DISTINCT gr.id)
            FROM GoodsReceipt gr
            JOIN gr.items gi
            WHERE gr.company.id = :companyId
              AND gr.archived = false
              AND gr.status = com.erp.domain.purchase.GoodsReceiptStatus.INSPECTED
              AND gi.acceptedQty IS NOT NULL
              AND gi.acceptedQty > 0
              AND gi.stockedAt IS NULL
            """)
    long countAwaitingStockReceive(@Param("companyId") Long companyId);
}
