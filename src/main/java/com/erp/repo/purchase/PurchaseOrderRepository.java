package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

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
}
