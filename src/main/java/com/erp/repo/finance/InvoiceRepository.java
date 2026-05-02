package com.erp.repo.finance;

import com.erp.domain.finance.Invoice;
import com.erp.domain.InvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceId(String invoiceId);

    List<Invoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Invoice> findByCompany_IdAndTypeOrderByCreatedAtDesc(Long companyId, InvoiceType type);

    Invoice findByOrderId(Long orderId);
    Optional<Invoice> findByOrderIdAndType(Long orderId, InvoiceType type);

    Optional<Invoice> findByCompany_IdAndOrderIdAndTypeAndSupplierInvoiceNumber(
            Long companyId,
            Long orderId,
            InvoiceType type,
            String supplierInvoiceNumber
    );

    List<Invoice> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);

    List<Invoice> findByToPartyOrderByCreatedAtDesc(String toParty); // customer name/id

    List<Invoice> findByCompany_IdAndToPartyOrderByCreatedAtDesc(Long companyId, String toParty);
}
