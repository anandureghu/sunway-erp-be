package com.erp.dto.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.InvoiceDocumentSource;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequest {
    private String toParty;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal amount;
    /** When set, persisted on the invoice (purchase/sales breakdown). */
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private String itemDescription;
    private String notesRemarks;
    private Integer gracePeriod;
    private BigDecimal interestRate;
    private String partyClassification;
    private InvoiceType type;
    private Long orderId;
    private Long creditAccount;
    private Long debitAccount;
    private Long bankAccountId;

    /** Vendor's invoice reference; used with purchase orders to avoid duplicates. */
    private String supplierInvoiceNumber;

    /** Defaults to GENERATED when omitted. */
    private InvoiceDocumentSource documentSource;

    /** Supplier portal / external document URL when documentSource is EXTERNAL_LINK. */
    private String externalDocumentUrl;
}
