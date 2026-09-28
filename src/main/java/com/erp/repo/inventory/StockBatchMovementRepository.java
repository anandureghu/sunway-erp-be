package com.erp.repo.inventory;

import com.erp.domain.inventory.StockBatchMovement;
import com.erp.domain.inventory.StockBatchMovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockBatchMovementRepository extends JpaRepository<StockBatchMovement, Long> {

    List<StockBatchMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
            String referenceType,
            Long referenceId
    );

    List<StockBatchMovement> findByReferenceTypeAndReferenceIdAndMovementTypeOrderByCreatedAtDesc(
            String referenceType,
            Long referenceId,
            StockBatchMovementType movementType
    );

    @Query("""
            SELECT m FROM StockBatchMovement m
            JOIN FETCH m.stockBatch sb
            JOIN FETCH sb.item i
            JOIN FETCH sb.warehouse w
            WHERE sb.company.id = :companyId
              AND (:itemId IS NULL OR i.id = :itemId)
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
              AND m.archived = :archived
            ORDER BY m.createdAt DESC
            """)
    List<StockBatchMovement> findHistory(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId,
            @Param("archived") boolean archived,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(m) FROM StockBatchMovement m
            JOIN m.stockBatch sb
            JOIN sb.item i
            WHERE sb.company.id = :companyId
              AND (:itemId IS NULL OR i.id = :itemId)
              AND (:warehouseId IS NULL OR sb.warehouse.id = :warehouseId)
              AND m.archived = :archived
            """)
    long countHistory(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId,
            @Param("archived") boolean archived
    );

    @Query("""
            SELECT m FROM StockBatchMovement m
            JOIN FETCH m.stockBatch sb
            JOIN FETCH sb.item i
            JOIN FETCH sb.warehouse w
            WHERE m.id = :id
              AND sb.company.id = :companyId
            """)
    Optional<StockBatchMovement> findByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") Long companyId
    );

    void deleteByStockBatchId(Long stockBatchId);

    /**
     * STOCK_SHEET_REPORTS — last outbound movement per item (SALE / TRANSFER_OUT).
     * Delete when Stock Summary / Item Summary report pages are retired.
     */
    @Query("""
            SELECT i.id, MAX(m.createdAt)
            FROM StockBatchMovement m
            JOIN m.stockBatch sb
            JOIN sb.item i
            WHERE sb.company.id = :companyId
              AND m.archived = false
              AND m.movementType IN (
                    com.erp.domain.inventory.StockBatchMovementType.SALE,
                    com.erp.domain.inventory.StockBatchMovementType.TRANSFER_OUT
              )
            GROUP BY i.id
            """)
    List<Object[]> lastOutboundByItem(@Param("companyId") Long companyId);

    /**
     * STOCK_SHEET_REPORTS — delete when Stock Summary / Item Summary pages are retired.
     */
    @Query("""
            SELECT m FROM StockBatchMovement m
            JOIN FETCH m.stockBatch sb
            JOIN FETCH sb.warehouse w
            WHERE sb.company.id = :companyId
              AND sb.item.id = :itemId
              AND m.archived = false
            ORDER BY m.createdAt DESC
            """)
    List<StockBatchMovement> findRecentForItem(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            Pageable pageable
    );
}
