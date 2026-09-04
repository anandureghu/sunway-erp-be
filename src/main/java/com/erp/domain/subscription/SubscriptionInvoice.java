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
@Table(name = "subscription_invoices")
public class SubscriptionInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_subscription_id", nullable = false)
    private Long companySubscriptionId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "invoice_no", nullable = false, length = 40, unique = true)
    private String invoiceNo;

    @Column(name = "period_key", nullable = false, length = 40)
    private String periodKey;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private SubscriptionPlanType planType;

    @Column(name = "pdf_path", length = 500)
    private String pdfPath;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generated_by", length = 50)
    private String generatedBy;

    @Column(name = "to_email", length = 500)
    private String toEmail;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by", length = 50)
    private String sentBy;

    @Builder.Default
    @Column(name = "send_success", nullable = false)
    private boolean sendSuccess = false;

    @Column(name = "send_error", length = 1000)
    private String sendError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;
}
