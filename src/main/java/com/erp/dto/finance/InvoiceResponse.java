package com.erp.dto.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.InvoiceDocumentSource;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class InvoiceResponse {

    private Long id;
    private String invoiceId;

    private Long companyId;
    private String companyName;

    private String toParty;
    private String status;

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private BigDecimal amount;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;

    private String itemDescription;
    private String notesRemarks;

    private Integer gracePeriod;
    private BigDecimal interestRate;

    private String partyClassification;
    private String pdfUrl;
    private String supplierInvoiceNumber;
    private InvoiceDocumentSource documentSource;
    private String externalDocumentUrl;

    private InvoiceType type;
    private Long orderId;

    private Long creditAccountId;
    private Long debitAccountId;

    private String creditAccountName;
    private String debitAccountName;
    private Long bankAccountId;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankIfscCode;
    private String bankBranchName;

    private Instant createdAt;

    private SalesOrderResponseDTO salesOrder;
    private PurchaseOrderResponseDTO purchaseOrder;
}
