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
    private String companyStreet;
    private String companyCity;
    private String companyState;
    private String companyCountry;
    private String companyPhone;
    private String companyEmail;
    private String billingEmail;
    private String companyWebsiteUrl;

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
    private String invoiceHeaderSubtitle;
    private String invoiceNotesUnpaid;
    private String invoiceNotesPaid;
    private String invoiceTerms;
    private String invoiceFooterCompanyLine;
    private String invoiceFooterTaxLine;
    private String invoiceFooterSignatureNote;
    private String invoiceFooterSupportEmail;
    private String invoiceFooterBillingEmail;
    private boolean invoiceQrEnabled;
    private String publicInvoiceUrl;

    private Instant createdAt;

    private SalesOrderResponseDTO salesOrder;
    private PurchaseOrderResponseDTO purchaseOrder;
}
