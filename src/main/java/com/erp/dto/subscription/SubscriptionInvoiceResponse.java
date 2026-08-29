package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionPlanType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionInvoiceResponse {

    private Long id;
    private Long companySubscriptionId;
    private Long companyId;
    private String invoiceNo;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String currencyCode;
    private SubscriptionPlanType planType;
    private String pdfUrl;
    private Instant generatedAt;
    private String generatedBy;
    /** True when a PDF has been generated and is ready to review/send. */
    private boolean generated;
    /** True when subscription details differ from the invoice snapshot (regenerate required). */
    private boolean stale;
    private List<String> recipientPreview;
    private String toEmail;
    private Instant sentAt;
    private String sentBy;
    private boolean sendSuccess;
    private String sendError;
    private boolean sent;
    private Instant createdAt;
}
