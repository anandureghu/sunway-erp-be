package com.erp.repo.finance;

import com.erp.domain.finance.Invoice;
import com.erp.domain.InvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceId(String invoiceId);

    List<Invoice> findByCompanyId(Long companyId);

    Invoice findByOrderId(Long orderId);
    Optional<Invoice> findByOrderIdAndType(Long orderId, InvoiceType type);

    List<Invoice> findByCompanyIdAndStatus(Long companyId, String status);

    List<Invoice> findByToParty(String toParty); // customer name/id
}
