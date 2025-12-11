package com.erp.dto.finance;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentResponseDTO {
    private Long id;
    private String paymentCode;
    private Long companyId;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDate effectiveDate;
    private String invoiceId;
    private String pdfUrl;
    private Instant createdAt;
}