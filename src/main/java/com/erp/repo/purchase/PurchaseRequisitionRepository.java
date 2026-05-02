package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseRequisition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {
    List<PurchaseRequisition> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
