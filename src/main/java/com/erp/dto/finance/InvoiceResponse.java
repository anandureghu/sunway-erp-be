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
public class InvoiceResponse {
    private Long id;
    private String invoiceId;
    private String customerName;
    private String orderName;
    private String status;
    private BigDecimal amount;
    private BigDecimal outstanding;
    private Instant dueDate;
    private Instant paidDate;
    private String notesRemarks;

    // Getters and Setters
}
