package com.erp.repo.finance;


import com.erp.domain.finance.Reconciliation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReconciliationRepository
        extends JpaRepository<Reconciliation, Long> {

    Optional<Reconciliation> findByIdAndCompanyId(Long id, Long companyId);
    
    Page<Reconciliation> findAllByCompanyId(Long companyId, Pageable pageable);
}
