package com.erp.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw text extracted from an uploaded PDF for manual review (not structured field extraction).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePdfTextPreviewResponse {
    private String text;
}
