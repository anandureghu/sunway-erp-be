package com.erp.repo.inventory;

import com.erp.domain.inventory.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    @Query("""
            SELECT sb FROM StockBatch sb
            WHERE sb.company.id = :companyId
              AND sb.item.id = :itemId
              AND (:warehouseId IS NULL OR sb.warehouse.id = :warehouseId)
              AND sb.quantityOnHand > 0
            ORDER BY sb.receivedAt ASC, sb.id ASC
            """)
    List<StockBatch> findAvailableFifo(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId
    );

    Optional<StockBatch> findByCompanyIdAndItemIdAndWarehouseIdAndBatchNoAndUnitCost(
            Long companyId,
            Long itemId,
            Long warehouseId,
            String batchNo,
            java.math.BigDecimal unitCost
    );

    /**
     * STOCK_SHEET_REPORTS — items with on-hand batches expiring on or before {@code until}.
     * Delete when Stock Summary / Item Summary report pages are retired.
     */
    @Query("""
            SELECT DISTINCT i.id
            FROM StockBatch sb
            JOIN sb.item i
            WHERE sb.company.id = :companyId
              AND sb.quantityOnHand > 0
              AND sb.expiryDate IS NOT NULL
              AND sb.expiryDate >= :today
              AND sb.expiryDate <= :until
            """)
    List<Long> itemIdsWithExpiringBatches(
            @Param("companyId") Long companyId,
            @Param("today") java.time.LocalDate today,
            @Param("until") java.time.LocalDate until
    );

    /**
     * STOCK_SHEET_REPORTS — weighted average unit cost per item from on-hand layers.
     * Delete when Stock Summary / Item Summary report pages are retired.
     */
    @Query("""
            SELECT i.id,
                   COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0),
                   COALESCE(SUM(sb.quantityOnHand), 0)
            FROM StockBatch sb
            JOIN sb.item i
            WHERE sb.company.id = :companyId
              AND sb.quantityOnHand > 0
            GROUP BY i.id
            """)
    List<Object[]> batchCostTotalsByItem(@Param("companyId") Long companyId);

    @Query("""
            SELECT sb FROM StockBatch sb
            JOIN FETCH sb.item i
            JOIN FETCH sb.warehouse w
            WHERE sb.company.id = :companyId
              AND sb.item.id = :itemId
              AND (:warehouseId IS NULL OR sb.warehouse.id = :warehouseId)
            ORDER BY sb.receivedAt DESC, sb.id DESC
            """)
    List<StockBatch> findByItemForCompany(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId
    );

    @Query("""
            SELECT sb FROM StockBatch sb
            JOIN FETCH sb.item i
            JOIN FETCH sb.warehouse w
            WHERE sb.company.id = :companyId
              AND sb.quantityOnHand > 0
              AND i.archived = false
              AND (:warehouseId IS NULL OR sb.warehouse.id = :warehouseId)
              AND (:itemId IS NULL OR sb.item.id = :itemId)
              AND (:batchNo IS NULL OR :batchNo = '' OR LOWER(sb.batchNo) LIKE LOWER(CONCAT('%', :batchNo, '%')))
            ORDER BY sb.receivedAt ASC, sb.batchNo ASC
            """)
    List<StockBatch> findForReport(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId,
            @Param("batchNo") String batchNo
    );

    @Query("""
            SELECT COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0)
            FROM StockBatch sb
            JOIN sb.item i
            JOIN sb.warehouse w
            WHERE sb.company.id = :companyId
              AND i.archived = false
              AND sb.quantityOnHand > 0
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            """)
    java.math.BigDecimal sumBatchValueAtCost(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category
    );

    /** Rows of (warehouseId, warehouseName, onHand, valueAtCost). */
    @Query("""
            SELECT w.id, w.name,
                   COALESCE(SUM(sb.quantityOnHand), 0),
                   COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0)
            FROM StockBatch sb
            JOIN sb.item i
            JOIN sb.warehouse w
            WHERE sb.company.id = :companyId
              AND i.archived = false
              AND sb.quantityOnHand > 0
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            GROUP BY w.id, w.name
            ORDER BY w.name
            """)
    List<Object[]> aggregateBatchValueByWarehouse(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category
    );

    /** Rows of (category, skuCount, onHand, valueAtCost). */
    @Query("""
            SELECT COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized'),
                   COUNT(DISTINCT i.id),
                   COALESCE(SUM(sb.quantityOnHand), 0),
                   COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0)
            FROM StockBatch sb
            JOIN sb.item i
            JOIN sb.warehouse w
            WHERE sb.company.id = :companyId
              AND i.archived = false
              AND sb.quantityOnHand > 0
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            GROUP BY COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized')
            ORDER BY COALESCE(NULLIF(TRIM(i.category), ''), 'Uncategorized')
            """)
    List<Object[]> aggregateBatchValueByCategory(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category
    );

    /**
     * Top stock lines by batch cost. Rows of
     * (itemId, sku, name, warehouseId, warehouseName, qtyOnHand, valueAtCost).
     */
    @Query("""
            SELECT i.id, i.sku, i.name, w.id, w.name,
                   COALESCE(SUM(sb.quantityOnHand), 0),
                   COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0)
            FROM StockBatch sb
            JOIN sb.item i
            JOIN sb.warehouse w
            WHERE sb.company.id = :companyId
              AND i.archived = false
              AND sb.quantityOnHand > 0
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            GROUP BY i.id, i.sku, i.name, w.id, w.name
            ORDER BY COALESCE(SUM(sb.quantityOnHand * sb.unitCost), 0) DESC
            """)
    List<Object[]> topBatchLinesByValue(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(sb.quantityOnHand), 0)
            FROM StockBatch sb
            WHERE sb.company.id = :companyId
              AND sb.item.id = :itemId
              AND sb.warehouse.id = :warehouseId
            """)
    int sumQuantityOnHand(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId
    );
}
