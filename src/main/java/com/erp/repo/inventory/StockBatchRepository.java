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
              AND sb.quantityOnHand > 0
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND (:category IS NULL OR :category = '' OR i.category = :category)
            """)
    java.math.BigDecimal sumBatchValueAtCost(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("category") String category
    );
}
