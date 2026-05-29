package com.erp.repo.purchase;

import com.erp.domain.purchase.PurchaseRequisitionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequisitionDocumentRepository
        extends JpaRepository<PurchaseRequisitionDocument, Long> {

    List<PurchaseRequisitionDocument> findByRequisitionIdOrderByUploadedAtDesc(Long requisitionId);

    Optional<PurchaseRequisitionDocument> findByIdAndRequisitionId(Long id, Long requisitionId);
}
