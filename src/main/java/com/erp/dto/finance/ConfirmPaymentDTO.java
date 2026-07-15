package com.erp.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmPaymentDTO {
    /** CASH, CARD, BANK_TRANSFER, CHEQUE, UPI, OTHER */
    private String paymentMethod;
    /** Amount to confirm; defaults to min(pending payment amount, invoice outstanding) when omitted. */
    private BigDecimal amount;
    /** Amount of the party's available credit note balance to apply to this payment, if any. */
    private BigDecimal applyCreditAmount;
}
