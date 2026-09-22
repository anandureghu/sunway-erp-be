package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceBrandingSettingsDTO {
    /** @deprecated Prefer unpaid/paid fields; kept for backward compatibility. */
    private String invoiceHeaderSubtitle;
    private String invoiceHeaderSubtitleUnpaid;
    private String invoiceHeaderSubtitlePaid;
    private String invoiceNotesUnpaid;
    private String invoiceNotesPaid;
    private String invoiceTerms;
    private String invoiceFooterCompanyLine;
    private String invoiceFooterTaxLine;
    private String invoiceFooterSignatureNote;
    private String invoiceFooterSupportEmail;
    private String invoiceFooterBillingEmail;
    private Boolean invoiceQrEnabled;
}
