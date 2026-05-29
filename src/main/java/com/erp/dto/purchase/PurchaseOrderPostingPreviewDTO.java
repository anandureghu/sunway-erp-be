package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderPostingPreviewDTO {
    /** {@code release} or {@code cancel} */
    private String action;
    private BigDecimal amount;
    private Long debitAccountId;
    private String debitAccountCode;
    private String debitAccountName;
    private BigDecimal debitBalanceBefore;
    private BigDecimal debitBalanceAfter;
    private Long creditAccountId;
    private String creditAccountCode;
    private String creditAccountName;
    private BigDecimal creditBalanceBefore;
    private BigDecimal creditBalanceAfter;
    /** True when release can proceed without insufficient-funds errors. */
    private boolean sufficientFunds;
    private String insufficientFundsMessage;
    /** Encumbrance already posted for this PO. */
    private boolean fundsAlreadyCommitted;
    /** For cancel: encumbrance exists and will be reversed. */
    private boolean willReleaseCommittedFunds;
    private String summary;
}
