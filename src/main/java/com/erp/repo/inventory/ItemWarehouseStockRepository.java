package com.erp.repo.inventory;

import com.erp.domain.inventory.ItemWarehouseStock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemWarehouseStockRepository extends JpaRepository<ItemWarehouseStock, Long> {

    Optional<ItemWarehouseStock> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    List<ItemWarehouseStock> findByItemId(Long itemId);

    @Query("""
            SELECT iws FROM ItemWarehouseStock iws
            JOIN FETCH iws.item i
            JOIN FETCH iws.warehouse w
            WHERE i.company.id = :companyId
            ORDER BY i.name ASC, w.name ASC
            """)
    List<ItemWarehouseStock> findAllByCompanyId(@Param("companyId") Long companyId);

    @Query("""
            SELECT COUNT(DISTINCT i.id)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            """)
    long countDistinctItemsForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);

    @Query("""
            SELECT COALESCE(SUM(iws.quantityOnHand), 0),
                   COALESCE(SUM(iws.reserved), 0),
                   COALESCE(SUM(CASE WHEN (iws.quantityOnHand - iws.reserved) < 0 THEN 0
                                     ELSE (iws.quantityOnHand - iws.reserved) END), 0),
                   COALESCE(SUM(iws.quantityOnHand * COALESCE(i.costPrice, 0)), 0),
                   COALESCE(SUM(iws.quantityOnHand * COALESCE(i.sellingPrice, 0)), 0)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            """)
    Object[] sumTotalsForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);

    @Query("""
            SELECT w.id, w.name,
                   COALESCE(SUM(iws.quantityOnHand), 0),
                   COALESCE(SUM(iws.reserved), 0),
                   COALESCE(SUM(CASE WHEN (iws.quantityOnHand - iws.reserved) < 0 THEN 0
                                     ELSE (iws.quantityOnHand - iws.reserved) END), 0),
                   COALESCE(SUM(iws.quantityOnHand * COALESCE(i.costPrice, 0)), 0)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            GROUP BY w.id, w.name
            ORDER BY w.name
            """)
    List<Object[]> aggregateByWarehouse(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized'),
                   COUNT(DISTINCT i.id),
                   COALESCE(SUM(iws.quantityOnHand), 0),
                   COALESCE(SUM(iws.quantityOnHand * COALESCE(i.costPrice, 0)), 0)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            GROUP BY COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized')
            ORDER BY COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized')
            """)
    List<Object[]> aggregateByCategory(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);

    @Query("""
            SELECT iws FROM ItemWarehouseStock iws
            JOIN FETCH iws.item i
            JOIN FETCH iws.warehouse w
            WHERE i.company.id = :companyId
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            ORDER BY (iws.quantityOnHand * COALESCE(i.costPrice, 0)) DESC
            """)
            List<ItemWarehouseStock> findStockLinesOrderByValueDesc(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category,
            Pageable pageable);
}
