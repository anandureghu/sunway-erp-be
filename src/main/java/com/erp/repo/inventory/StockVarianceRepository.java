package com.erp.repo.inventory;

import com.erp.domain.inventory.StockVariance;
import com.erp.domain.inventory.StockVarianceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockVarianceRepository extends JpaRepository<StockVariance, Long> {

    List<StockVariance> findByCompanyIdAndVarianceStatusOrderByCreatedAtDesc(
            Long companyId,
            StockVarianceStatus status
    );

    List<StockVariance> findByCompanyIdAndVarianceStatusInOrderByCreatedAtDesc(
            Long companyId,
            List<StockVarianceStatus> statuses
    );
}
