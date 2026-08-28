package com.erp.repo.subscription;

import com.erp.domain.subscription.SubscriptionInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, Long> {

    List<SubscriptionInvoice> findByCompanySubscriptionIdOrderByCreatedAtDesc(Long companySubscriptionId);

    List<SubscriptionInvoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<SubscriptionInvoice> findByIdAndCompanyId(Long id, Long companyId);

    Optional<SubscriptionInvoice> findByCompanySubscriptionIdAndPeriodKey(
            Long companySubscriptionId,
            String periodKey
    );

    long countByCompanyId(Long companyId);
}
