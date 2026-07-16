package com.erp.repo.finance;


import com.erp.domain.finance.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

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
