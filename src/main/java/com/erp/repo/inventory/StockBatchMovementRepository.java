package com.erp.repo.inventory;

import com.erp.domain.inventory.StockBatchMovement;
import com.erp.domain.inventory.StockBatchMovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
            ORDER BY m.createdAt DESC
            """)
    List<StockBatchMovement> findHistory(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(m) FROM StockBatchMovement m
            JOIN m.stockBatch sb
            JOIN sb.item i
            WHERE sb.company.id = :companyId
              AND (:itemId IS NULL OR i.id = :itemId)
              AND (:warehouseId IS NULL OR sb.warehouse.id = :warehouseId)
            """)
    long countHistory(
            @Param("companyId") Long companyId,
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId
    );
}
