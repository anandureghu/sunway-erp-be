package com.erp.dto.finance;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreatePaymentDTO {
    private Long companyId;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDate effectiveDate;
    private String notes;
    private String invoiceId; // optional: link to existing invoice or null => create invoice
}