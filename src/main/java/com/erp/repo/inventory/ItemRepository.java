package com.erp.repo.inventory;

import com.erp.domain.inventory.Item;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    boolean existsBySkuAndCompanyId(String sku, Long companyId);

    /**
     * @deprecated Prefer {@link ItemWarehouseStockRepository#findLowStockLinesForReport} —
     * low stock is evaluated per warehouse stock row, not Item.available aggregates.
     */
    @Query("""
            SELECT i FROM Item i
            WHERE i.company.id = :companyId
              AND i.reorderLevel IS NOT NULL
              AND COALESCE(i.available, 0) <= i.reorderLevel
              AND (:category IS NULL OR :category = '' OR i.category = :category)
              AND (:warehouseId IS NULL OR EXISTS (
                SELECT 1 FROM ItemWarehouseStock s
                WHERE s.item.id = i.id AND s.warehouse.id = :warehouseId
              ))
            ORDER BY COALESCE(i.available, 0) ASC, i.sku
            """)
    List<Item> findLowStockForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category,
            Pageable pageable);

    /**
     * @deprecated Prefer {@link ItemWarehouseStockRepository#countLowStockLinesForReport}.
     */
    @Query("""
            SELECT COUNT(i) FROM Item i
            WHERE i.company.id = :companyId
              AND i.reorderLevel IS NOT NULL
              AND COALESCE(i.available, 0) <= i.reorderLevel
              AND (:category IS NULL OR :category = '' OR i.category = :category)
              AND (:warehouseId IS NULL OR EXISTS (
                SELECT 1 FROM ItemWarehouseStock s
                WHERE s.item.id = i.id AND s.warehouse.id = :warehouseId
              ))
            """)
    long countLowStockForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);

    List<Item> findByCompanyIdAndArchivedOrderByCreatedAtDesc(Long companyId, boolean archived);

    @Query("SELECT COUNT(poi) FROM PurchaseOrderItem poi WHERE poi.item.id = :itemId")
    long countPurchaseOrderLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(pri) FROM PurchaseRequisitionItem pri WHERE pri.item.id = :itemId")
    long countPurchaseRequisitionLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(soi) FROM SalesOrderItem soi WHERE soi.item.id = :itemId")
    long countSalesOrderLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(gri) FROM GoodsReceiptItem gri WHERE gri.item.id = :itemId")
    long countGoodsReceiptLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(pi) FROM PicklistItem pi WHERE pi.item.id = :itemId")
    long countPicklistLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(si) FROM ShipmentItem si WHERE si.item.id = :itemId")
    long countShipmentLineRefs(@Param("itemId") Long itemId);

    @Query("SELECT COUNT(sv) FROM StockVariance sv WHERE sv.item.id = :itemId")
    long countStockVarianceRefs(@Param("itemId") Long itemId);
}
