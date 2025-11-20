package com.erp.repo.finance;

import com.erp.domain.finance.Invoices;
import com.erp.domain.inventory.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoices, Long> {
    List<Invoices> findByCustomer(Customer customer);
    List<Invoices> findByStatus(String status);
    Invoices findByInvoiceId(String invoiceId);
}
