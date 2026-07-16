package com.erp.domain.finance;

/**
 * {@link #CUSTOMER} — receipt against a sales invoice (AR).
 * {@link #VENDOR} — payable / payment toward a purchase order (AP).
 * {@link #OTHER} — ad-hoc business expense not tied to a PO or invoice (rent, reimbursements, etc.).
 */
public enum PaymentDirection {
    CUSTOMER,
    VENDOR,
    OTHER
}
