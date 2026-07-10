package com.erp.repo.purchase;

import com.erp.domain.purchase.GoodsReceipt;
import com.erp.domain.purchase.GoodsReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    List<GoodsReceipt> findByPurchaseOrderId(Long purchaseOrderId);

    List<GoodsReceipt> findByCompany_IdOrderByReceivedAtDesc(Long companyId);

    List<GoodsReceipt> findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(Long companyId);

    Page<GoodsReceipt> findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(Long companyId, Pageable pageable);

    List<GoodsReceipt> findByCompany_IdAndStatusAndArchivedFalseOrderByReceivedAtDesc(
            Long companyId, GoodsReceiptStatus status);
}
