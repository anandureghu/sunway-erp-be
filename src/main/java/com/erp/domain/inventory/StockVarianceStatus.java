package com.erp.domain.inventory;

public enum StockVarianceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Returned to the requester with a reason; they can revise and resubmit it as a new pending request. */
    SENT_BACK
}
