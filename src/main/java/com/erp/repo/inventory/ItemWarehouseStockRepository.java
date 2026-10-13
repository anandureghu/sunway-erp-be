package com.erp.repo.inventory;

import com.erp.domain.inventory.ItemWarehouseStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemWarehouseStockRepository extends JpaRepository<ItemWarehouseStock, Long> {

    Optional<ItemWarehouseStock> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    List<ItemWarehouseStock> findByItemId(Long itemId);

    List<ItemWarehouseStock> findByWarehouseId(Long warehouseId);

    @Query("""
            SELECT iws FROM ItemWarehouseStock iws
            JOIN FETCH iws.item i
            JOIN FETCH iws.warehouse w
            WHERE i.company.id = :companyId
              AND i.archived = :archived
            ORDER BY i.name ASC, w.name ASC
            """)
    List<ItemWarehouseStock> findAllByCompanyIdAndArchived(
            @Param("companyId") Long companyId,
            @Param("archived") boolean archived
    );

    @Query(
            value = """
                    SELECT iws FROM ItemWarehouseStock iws
                    JOIN FETCH iws.item i
                    JOIN FETCH iws.warehouse w
                    WHERE i.company.id = :companyId
                      AND i.archived = :archived
                      AND (
                           :search IS NULL OR :search = ''
                           OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.barcode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.category, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.subCategory, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(w.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                      )
                      AND (
                           :status IS NULL OR :status = '' OR :status = 'all'
                           OR (
                                :status = 'discontinued'
                                AND LOWER(COALESCE(i.status, '')) = 'discontinued'
                           )
                           OR (
                                :status = 'active'
                                AND LOWER(COALESCE(i.status, '')) <> 'discontinued'
                                AND COALESCE(iws.quantityOnHand, 0) > 0
                           )
                           OR (
                                :status = 'out_of_stock'
                                AND LOWER(COALESCE(i.status, '')) <> 'discontinued'
                                AND COALESCE(iws.quantityOnHand, 0) <= 0
                           )
                      )
                      AND (
                           :stockFilter IS NULL OR :stockFilter = '' OR :stockFilter = 'all'
                           OR (
                                :stockFilter = 'low_stock'
                                AND i.reorderLevel IS NOT NULL
                                AND (CASE WHEN (COALESCE(iws.quantityOnHand, 0) - COALESCE(iws.reserved, 0)) < 0
                                          THEN 0
                                          ELSE (COALESCE(iws.quantityOnHand, 0) - COALESCE(iws.reserved, 0))
                                     END) <= i.reorderLevel
                           )
                           OR (
                                :stockFilter = 'on_reserve'
                                AND COALESCE(iws.reserved, 0) > 0
                           )
                      )
                    ORDER BY i.name ASC, w.name ASC
                    """,
            countQuery = """
                    SELECT COUNT(iws) FROM ItemWarehouseStock iws
                    JOIN iws.item i
                    JOIN iws.warehouse w
                    WHERE i.company.id = :companyId
                      AND i.archived = :archived
                      AND (
                           :search IS NULL OR :search = ''
                           OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.barcode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.category, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(i.subCategory, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(w.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                      )
                      AND (
                           :status IS NULL OR :status = '' OR :status = 'all'
                           OR (
                                :status = 'discontinued'
                                AND LOWER(COALESCE(i.status, '')) = 'discontinued'
                           )
                           OR (
                                :status = 'active'
                                AND LOWER(COALESCE(i.status, '')) <> 'discontinued'
                                AND COALESCE(iws.quantityOnHand, 0) > 0
                           )
                           OR (
                                :status = 'out_of_stock'
                                AND LOWER(COALESCE(i.status, '')) <> 'discontinued'
                                AND COALESCE(iws.quantityOnHand, 0) <= 0
                           )
                      )
                      AND (
                           :stockFilter IS NULL OR :stockFilter = '' OR :stockFilter = 'all'
                           OR (
                                :stockFilter = 'low_stock'
                                AND i.reorderLevel IS NOT NULL
                                AND (CASE WHEN (COALESCE(iws.quantityOnHand, 0) - COALESCE(iws.reserved, 0)) < 0
                                          THEN 0
                                          ELSE (COALESCE(iws.quantityOnHand, 0) - COALESCE(iws.reserved, 0))
                                     END) <= i.reorderLevel
                           )
                           OR (
                                :stockFilter = 'on_reserve'
                                AND COALESCE(iws.reserved, 0) > 0
                           )
                      )
                    """
    )
    Page<ItemWarehouseStock> searchStockCatalog(
            @Param("companyId") Long companyId,
            @Param("archived") boolean archived,
            @Param("search") String search,
            @Param("status") String status,
            @Param("stockFilter") String stockFilter,
            Pageable pageable
    );

    void deleteByItemId(Long itemId);

    @Query("""
            SELECT COUNT(DISTINCT i.id)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND i.archived = false
              AND iws.quantityOnHand > 0
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
              AND i.archived = false
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
              AND i.archived = false
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
              AND i.archived = false
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
              AND i.archived = false
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            ORDER BY (iws.quantityOnHand * COALESCE(i.costPrice, 0)) DESC
            """)
    List<ItemWarehouseStock> findStockLinesOrderByValueDesc(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            SELECT iws FROM ItemWarehouseStock iws
            JOIN FETCH iws.item i
            JOIN FETCH iws.warehouse w
            WHERE i.company.id = :companyId
              AND i.archived = false
              AND i.reorderLevel IS NOT NULL
              AND (CASE WHEN (iws.quantityOnHand - iws.reserved) < 0 THEN 0
                        ELSE (iws.quantityOnHand - iws.reserved) END) <= i.reorderLevel
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            ORDER BY (CASE WHEN (iws.quantityOnHand - iws.reserved) < 0 THEN 0
                           ELSE (iws.quantityOnHand - iws.reserved) END) ASC, i.sku
            """)
    List<ItemWarehouseStock> findLowStockLinesForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            SELECT COUNT(iws)
            FROM ItemWarehouseStock iws
            JOIN iws.item i
            JOIN iws.warehouse w
            WHERE i.company.id = :companyId
              AND i.archived = false
              AND i.reorderLevel IS NOT NULL
              AND (CASE WHEN (iws.quantityOnHand - iws.reserved) < 0 THEN 0
                        ELSE (iws.quantityOnHand - iws.reserved) END) <= i.reorderLevel
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            """)
    long countLowStockLinesForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category);
}
