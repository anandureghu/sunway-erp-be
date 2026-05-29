package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.domain.purchase.PurchaseRequisitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {
    List<PurchaseRequisition> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT pr FROM PurchaseRequisition pr
            JOIN pr.items line
            WHERE pr.company.id = :companyId
              AND pr.deliveryWarehouse.id = :warehouseId
              AND line.item.id = :itemId
              AND pr.archived = false
              AND pr.status IN :statuses
              AND (:excludeRequisitionId IS NULL OR pr.id <> :excludeRequisitionId)
            ORDER BY pr.createdAt DESC
            """)
    List<PurchaseRequisition> findPendingRequisitionsForItemAtWarehouse(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId,
            @Param("statuses") List<PurchaseRequisitionStatus> statuses,
            @Param("excludeRequisitionId") Long excludeRequisitionId
    );

    default Optional<PurchaseRequisition> findFirstPendingForItemAtWarehouse(
            Long companyId,
            Long warehouseId,
            Long itemId,
            List<PurchaseRequisitionStatus> statuses,
            Long excludeRequisitionId
    ) {
        List<PurchaseRequisition> list = findPendingRequisitionsForItemAtWarehouse(
                companyId, warehouseId, itemId, statuses, excludeRequisitionId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
