package com.erp.domain.subscription;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_reminder_logs")
public class SubscriptionReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_subscription_id", nullable = false)
    private Long companySubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 20)
    private SubscriptionReminderType reminderType;

    /** Stable key for the billing period end date (ISO date) so each reminder fires once per period. */
    @Column(name = "period_key", nullable = false, length = 40)
    private String periodKey;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "to_email", length = 120)
    private String toEmail;

    @Builder.Default
    @Column(nullable = false)
    private boolean success = false;

    @Column(length = 1000)
    private String error;
}
