package com.erp.repo.sales;

import com.erp.domain.sales.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByPicklistId(Long picklistId);

    List<Shipment> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    long countByCompanyIdAndStatus(Long companyId, String status);

    @Query("""
            SELECT COUNT(s) FROM Shipment s
            WHERE s.company.id = :companyId
              AND s.status = 'DELIVERED'
              AND s.deliveredAt IS NOT NULL
              AND s.deliveredAt >= :from
            """)
    long countDeliveredSince(
            @Param("companyId") Long companyId,
            @Param("from") java.time.Instant from);

    @Query("""
            SELECT DISTINCT s FROM Shipment s
            JOIN FETCH s.customer
            JOIN FETCH s.picklist p
            JOIN FETCH p.salesOrder
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.item
            WHERE s.company.id = :companyId
            ORDER BY s.createdAt DESC
            """)
    List<Shipment> findByCompanyIdWithDetails(@Param("companyId") Long companyId);
}
