package com.erp.repo.finance;


import com.erp.domain.finance.Reconciliation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReconciliationRepository
        extends JpaRepository<Reconciliation, Long> {

    Optional<Reconciliation> findByIdAndCompanyId(Long id, Long companyId);
    
    Page<Reconciliation> findAllByCompanyId(Long companyId, Pageable pageable);

    @Query("""
            SELECT r FROM Reconciliation r
            WHERE r.company.id = :companyId
              AND (
                   :q IS NULL OR :q = ''
                   OR LOWER(COALESCE(r.account.accountName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(r.account.accountCode, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(r.reason, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(r.resource, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(CAST(r.status AS string)) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Reconciliation> searchByCompanyId(
            @Param("companyId") Long companyId,
            @Param("q") String q,
            Pageable pageable);
}
