package com.erp.repo.inventory;

import com.erp.domain.inventory.StockVariance;
import com.erp.domain.inventory.StockVarianceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockVarianceRepository extends JpaRepository<StockVariance, Long> {

    List<StockVariance> findByCompanyIdAndVarianceStatusOrderByCreatedAtDesc(
            Long companyId,
            StockVarianceStatus status
    );

    List<StockVariance> findByCompanyIdAndVarianceStatusAndCreatedBy_IdOrderByCreatedAtDesc(
            Long companyId,
            StockVarianceStatus status,
            Long createdById
    );

    List<StockVariance> findByCompanyIdAndVarianceStatusInAndArchivedOrderByCreatedAtDesc(
            Long companyId,
            List<StockVarianceStatus> statuses,
            boolean archived
    );

    Page<StockVariance> findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<StockVariance> findByCompanyIdAndArchivedTrue(Long companyId);
}
