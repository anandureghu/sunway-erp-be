package com.erp.domain.finance;

/**
 * {@link #CUSTOMER} — receipt against a sales invoice (AR).
 * {@link #VENDOR} — payable / payment toward a purchase order (AP).
 */
public enum PaymentDirection {
    CUSTOMER,
    VENDOR
}
