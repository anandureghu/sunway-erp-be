package com.erp.repo.sales;

import com.erp.domain.sales.Picklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PicklistRepository extends JpaRepository<Picklist, Long> {

    Optional<Picklist> findBySalesOrderId(Long salesOrderId);

    List<Picklist> findByCompanyId(Long companyId);
}
