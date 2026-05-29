package com.erp.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmPaymentDTO {
    /** Required for vendor (AP) payments: CASH, CARD, BANK_TRANSFER, CHEQUE, UPI, OTHER */
    private String paymentMethod;
}
