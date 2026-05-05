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
     * Low-stock items: available &lt;= reorder level (when reorder level is set).
     * When warehouseId is set, only items that have a stock row in that warehouse are included.
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
}
