package com.erp.repo.subscription;

import com.erp.domain.subscription.SubscriptionReminderLog;
import com.erp.domain.subscription.SubscriptionReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionReminderLogRepository extends JpaRepository<SubscriptionReminderLog, Long> {

    List<SubscriptionReminderLog> findByCompanySubscriptionIdOrderBySentAtDesc(Long companySubscriptionId);

    Optional<SubscriptionReminderLog> findByCompanySubscriptionIdAndReminderTypeAndPeriodKeyAndSuccessTrue(
            Long companySubscriptionId,
            SubscriptionReminderType reminderType,
            String periodKey
    );

    boolean existsByCompanySubscriptionIdAndReminderTypeAndPeriodKeyAndSuccessTrue(
            Long companySubscriptionId,
            SubscriptionReminderType reminderType,
            String periodKey
    );
}
