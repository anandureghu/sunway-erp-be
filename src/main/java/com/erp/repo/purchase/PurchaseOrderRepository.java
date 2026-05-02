package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<PurchaseOrder> findBySourceRequisition_Id(Long requisitionId);
}
