package com.erp.repo.finance;


import com.erp.domain.finance.CreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT c FROM CreditNote c
            LEFT JOIN c.invoice inv
            WHERE c.company.id = :companyId
              AND (
                   :q IS NULL OR :q = ''
                   OR LOWER(COALESCE(c.creditNoteNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(c.status, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(c.reason, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(c.project, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(c.source, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(inv.invoiceId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(inv.toParty, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<CreditNote> searchByCompanyId(
            @Param("companyId") Long companyId,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT c FROM CreditNote c
            WHERE c.company.id = :companyId
              AND c.customerId = :customerId
              AND c.status IN ('AVAILABLE', 'PARTIALLY_APPLIED')
              AND c.remainingAmount > 0
            ORDER BY c.createdAt ASC
            """)
    List<CreditNote> findAvailableForCustomer(
            @Param("companyId") Long companyId, @Param("customerId") Long customerId);

    @Query("""
            SELECT c FROM CreditNote c
            WHERE c.company.id = :companyId
              AND c.supplierId = :supplierId
              AND c.status IN ('AVAILABLE', 'PARTIALLY_APPLIED')
              AND c.remainingAmount > 0
            ORDER BY c.createdAt ASC
            """)
    List<CreditNote> findAvailableForSupplier(
            @Param("companyId") Long companyId, @Param("supplierId") Long supplierId);
}
