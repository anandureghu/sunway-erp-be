package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT DISTINCT po FROM PurchaseOrder po
            LEFT JOIN po.supplier s
            LEFT JOIN com.erp.domain.finance.Invoice inv
              ON inv.orderId = po.id
             AND inv.type = com.erp.domain.InvoiceType.PURCHASE
             AND inv.company.id = po.company.id
            WHERE po.company.id = :companyId
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(po.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.vendorName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                :tab IS NULL OR :tab = '' OR :tab = 'all'
                OR (:tab = 'open' AND po.status NOT IN (
                      com.erp.domain.purchase.PurchaseOrderStatus.RECEIVED,
                      com.erp.domain.purchase.PurchaseOrderStatus.CANCELLED
                    ) AND po.archived = false)
                OR (:tab = 'terminal' AND po.status IN (
                      com.erp.domain.purchase.PurchaseOrderStatus.RECEIVED,
                      com.erp.domain.purchase.PurchaseOrderStatus.CANCELLED
                    ) AND po.archived = false)
              )
              AND (
                :statusEnum IS NULL
                OR po.status = :statusEnum
              )
              AND (
                :paymentStatus IS NULL OR :paymentStatus = '' OR :paymentStatus = 'all'
                OR UPPER(COALESCE(inv.status, 'UNPAID')) = UPPER(:paymentStatus)
              )
              AND (:dateFrom IS NULL OR po.orderDate >= :dateFrom)
              AND (:dateTo IS NULL OR po.orderDate <= :dateTo)
            """)
    Page<PurchaseOrder> search(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("tab") String tab,
            @Param("statusEnum") PurchaseOrderStatus statusEnum,
            @Param("paymentStatus") String paymentStatus,
            @Param("dateFrom") java.time.LocalDate dateFrom,
            @Param("dateTo") java.time.LocalDate dateTo,
            Pageable pageable
    );

    Page<PurchaseOrder> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<PurchaseOrder> findByCompanyIdAndArchivedTrue(Long companyId);

    long countByCompanyIdAndArchivedFalseAndStatus(Long companyId, PurchaseOrderStatus status);

    long countByCompanyIdAndArchivedFalseAndStatusIn(Long companyId, List<PurchaseOrderStatus> statuses);

    List<PurchaseOrder> findBySupplier_IdAndArchivedFalseAndStatusInOrderByCreatedAtDesc(
            Long supplierId,
            List<PurchaseOrderStatus> statuses
    );

    Optional<PurchaseOrder> findBySourceRequisition_Id(Long requisitionId);

    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN po.items line
            JOIN po.sourceRequisition pr
            WHERE po.company.id = :companyId
              AND pr.deliveryWarehouse.id = :warehouseId
              AND line.item.id = :itemId
              AND po.archived = false
              AND po.status IN :statuses
            ORDER BY po.createdAt DESC
            """)
    List<PurchaseOrder> findPendingOrdersForItemAtWarehouse(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId,
            @Param("statuses") List<PurchaseOrderStatus> statuses
    );

    default Optional<PurchaseOrder> findFirstPendingForItemAtWarehouse(
            Long companyId,
            Long warehouseId,
            Long itemId,
            List<PurchaseOrderStatus> statuses
    ) {
        List<PurchaseOrder> list = findPendingOrdersForItemAtWarehouse(
                companyId, warehouseId, itemId, statuses);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("""
            SELECT COALESCE(SUM(
                    CASE
                        WHEN (poi.quantity - COALESCE(poi.receivedQty, 0) - COALESCE(poi.rejectedQty, 0)) < 0
                            THEN 0
                        ELSE (poi.quantity - COALESCE(poi.receivedQty, 0) - COALESCE(poi.rejectedQty, 0))
                    END
                ), 0)
            FROM PurchaseOrder po
            JOIN po.items poi
            WHERE po.company.id = :companyId
              AND po.archived = false
              AND po.status IN :openStatuses
            """)
    Long sumOnOrderQuantity(
            @Param("companyId") Long companyId,
            @Param("openStatuses") List<PurchaseOrderStatus> openStatuses
    );

    @Query("""
            SELECT poi.item.id,
                   COALESCE(SUM(
                    CASE
                        WHEN (poi.quantity - COALESCE(poi.receivedQty, 0) - COALESCE(poi.rejectedQty, 0)) < 0
                            THEN 0
                        ELSE (poi.quantity - COALESCE(poi.receivedQty, 0) - COALESCE(poi.rejectedQty, 0))
                    END
                ), 0)
            FROM PurchaseOrder po
            JOIN po.items poi
            WHERE po.company.id = :companyId
              AND po.archived = false
              AND po.status IN :openStatuses
            GROUP BY poi.item.id
            """)
    List<Object[]> sumOnOrderQuantityGroupedByItem(
            @Param("companyId") Long companyId,
            @Param("openStatuses") List<PurchaseOrderStatus> openStatuses
    );
}
