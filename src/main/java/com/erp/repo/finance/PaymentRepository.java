package com.erp.repo.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentCode(String code);
    List<Payment> findByCompanyId(Long companyId);
    List<Payment> findByCompany_IdAndPaymentDirection(Long companyId, PaymentDirection paymentDirection);
    List<Payment> findByInvoiceId(String invoiceId);

    Optional<Payment> findFirstByPurchaseOrderIdAndPaymentDirection(
            Long purchaseOrderId, PaymentDirection paymentDirection);
}
