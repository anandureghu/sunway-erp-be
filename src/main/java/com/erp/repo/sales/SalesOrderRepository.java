package com.erp.repo.sales;

import com.erp.domain.sales.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByCompanyId(Long companyId);
}
