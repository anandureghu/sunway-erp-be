package com.erp.repo.sales;

import com.erp.domain.sales.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<SalesReturn> findBySalesOrderIdAndCompanyIdOrderByCreatedAtDesc(Long salesOrderId, Long companyId);
}
