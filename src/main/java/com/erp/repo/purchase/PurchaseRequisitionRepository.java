package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.domain.purchase.PurchaseRequisitionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {
    List<PurchaseRequisition> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT pr FROM PurchaseRequisition pr
            LEFT JOIN pr.requestedBy rb
            LEFT JOIN pr.department d
            WHERE pr.company.id = :companyId
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(pr.requisitionNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(rb.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(d.departmentName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                :tab IS NULL OR :tab = '' OR :tab = 'all'
                OR (:tab = 'active' AND pr.status <> com.erp.domain.purchase.PurchaseRequisitionStatus.CONVERTED
                    AND pr.archived = false)
                OR (:tab = 'converted' AND pr.status = com.erp.domain.purchase.PurchaseRequisitionStatus.CONVERTED
                    AND pr.archived = false)
              )
              AND (
                :statusEnum IS NULL
                OR pr.status = :statusEnum
              )
            """)
    Page<PurchaseRequisition> search(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("tab") String tab,
            @Param("statusEnum") PurchaseRequisitionStatus statusEnum,
            Pageable pageable
    );

    Page<PurchaseRequisition> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<PurchaseRequisition> findByCompanyIdAndArchivedTrue(Long companyId);

    long countByCompanyIdAndArchivedFalseAndStatus(Long companyId, PurchaseRequisitionStatus status);

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
