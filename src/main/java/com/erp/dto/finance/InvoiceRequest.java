package com.erp.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {
    private String invoiceId;
    private Long customerId;
    private Long orderId;
    private String status;
    private Instant dueDate;
    private BigDecimal amount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;
    private BigDecimal interestRate;
    private String notesRemarks;
}
