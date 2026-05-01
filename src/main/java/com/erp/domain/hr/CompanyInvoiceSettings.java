package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "company_invoice_settings")
public class CompanyInvoiceSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "invoice_header_subtitle", length = 200)
    private String invoiceHeaderSubtitle;

    @Column(name = "invoice_notes_unpaid", length = 1000)
    private String invoiceNotesUnpaid;

    @Column(name = "invoice_notes_paid", length = 1000)
    private String invoiceNotesPaid;

    @Column(name = "invoice_terms", length = 4000)
    private String invoiceTerms;

    @Column(name = "invoice_footer_company_line", length = 300)
    private String invoiceFooterCompanyLine;

    @Column(name = "invoice_footer_tax_line", length = 300)
    private String invoiceFooterTaxLine;

    @Column(name = "invoice_footer_signature_note", length = 300)
    private String invoiceFooterSignatureNote;

    @Column(name = "invoice_footer_support_email", length = 120)
    private String invoiceFooterSupportEmail;

    @Column(name = "invoice_footer_billing_email", length = 120)
    private String invoiceFooterBillingEmail;

    @Column(name = "invoice_qr_enabled", nullable = false)
    private boolean invoiceQrEnabled;
}
