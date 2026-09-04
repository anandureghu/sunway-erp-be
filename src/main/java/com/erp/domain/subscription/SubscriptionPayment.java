package com.erp.domain.subscription;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_subscription_id", nullable = false)
    private Long companySubscriptionId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "subscription_invoice_id")
    private Long subscriptionInvoiceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "method_note", length = 255)
    private String methodNote;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "idempotency_key", length = 80, unique = true)
    private String idempotencyKey;

    @Column(name = "receipt_no", length = 40, unique = true)
    private String receiptNo;

    @Column(name = "receipt_generated_at")
    private Instant receiptGeneratedAt;

    @Column(name = "receipt_sent_at")
    private Instant receiptSentAt;

    @Column(name = "receipt_sent_by", length = 50)
    private String receiptSentBy;

    @Builder.Default
    @Column(name = "receipt_send_success", nullable = false)
    private boolean receiptSendSuccess = false;

    @Column(name = "receipt_send_error", length = 1000)
    private String receiptSendError;

    @Column(name = "receipt_to_email", length = 500)
    private String receiptToEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
