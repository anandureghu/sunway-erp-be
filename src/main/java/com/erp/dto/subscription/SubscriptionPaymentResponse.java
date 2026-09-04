package com.erp.dto.subscription;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPaymentResponse {
    private Long id;
    private Long companySubscriptionId;
    private Long companyId;
    private Long invoiceId;
    private String invoiceNo;
    private BigDecimal amount;
    private LocalDate paidOn;
    private String methodNote;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long recordedBy;
    private Instant createdAt;
    private String receiptNo;
    private Instant receiptGeneratedAt;
    private Instant receiptSentAt;
    private boolean receiptGenerated;
    private boolean receiptSent;
    private String receiptToEmail;
    private String receiptSendError;
}
