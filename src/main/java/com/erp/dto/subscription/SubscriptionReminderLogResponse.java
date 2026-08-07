package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionReminderType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionReminderLogResponse {
    private Long id;
    private SubscriptionReminderType reminderType;
    private String periodKey;
    private Instant sentAt;
    private String toEmail;
    private boolean success;
    private String error;
}
