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
    /** CUSTOMER = AR receipt; VENDOR = AP vendor payable / payment */
    private String paymentDirection;
    private Long purchaseOrderId;
    /** Business PO number when {@link #purchaseOrderId} is set. */
    private String purchaseOrderNumber;
    /** Business SO number for customer payments linked to a sales invoice. */
    private String salesOrderNumber;
    private String pdfUrl;
    private boolean archived;
    private Instant createdAt;
    /** Remaining balance on the linked invoice (for confirm dialog). */
    private BigDecimal invoiceOutstanding;
    /** Total amount on the linked invoice (for confirm dialog). */
    private BigDecimal invoiceTotal;
}