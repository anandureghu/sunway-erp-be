package com.erp.repo.sales;

import com.erp.domain.sales.Picklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PicklistRepository extends JpaRepository<Picklist, Long> {

    Optional<Picklist> findBySalesOrderId(Long salesOrderId);
    Optional<Picklist> findByCompanyIdAndSalesOrderId(Long companyId, Long salesOrderId);

    List<Picklist> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Picklist> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId);
    List<Picklist> findByCompanyIdAndArchivedTrue(Long companyId);
    Page<Picklist> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);
}
