package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionPlanType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    private String toEmail;
    private Instant sentAt;
    private String sentBy;
    private boolean sendSuccess;
    private String sendError;
    private boolean sent;
    private Instant createdAt;
}
