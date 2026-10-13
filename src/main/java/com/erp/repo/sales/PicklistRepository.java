package com.erp.repo.sales;

import com.erp.domain.sales.Picklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PicklistRepository extends JpaRepository<Picklist, Long> {

    Optional<Picklist> findBySalesOrderId(Long salesOrderId);
    Optional<Picklist> findByCompanyIdAndSalesOrderId(Long companyId, Long salesOrderId);

    List<Picklist> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Picklist> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId);
    List<Picklist> findByCompanyIdAndArchivedTrue(Long companyId);
    Page<Picklist> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    @Query("""
            SELECT p FROM Picklist p
            LEFT JOIN p.salesOrder so
            LEFT JOIN so.customer c
            WHERE p.company.id = :companyId
              AND p.archived = false
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(p.picklistNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(so.orderNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.customerName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.status) LIKE LOWER(CONCAT('%', :search, '%'))
              )
              AND (
                :status IS NULL OR :status = '' OR :status = 'all'
                OR UPPER(p.status) = UPPER(:status)
              )
              AND (
                :excludeDispatched = false
                OR NOT EXISTS (
                    SELECT 1 FROM com.erp.domain.sales.Shipment s WHERE s.picklist.id = p.id
                )
              )
            """)
    Page<Picklist> search(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("excludeDispatched") boolean excludeDispatched,
            Pageable pageable
    );
}
