package com.erp.dto.loan;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** A loan's payment record: every repayment with a running total. */
@Getter
@Builder
public class LoanPaymentRecordDTO {
    private Long loanId;
    private String loanCode;
    private String status;
    private Double loanAmount;
    /** Sum of all recorded repayments. */
    private Double totalPaid;
    /** Current loan balance. */
    private Double balance;
    private String currencyCode;
    /**
     * True when the recorded repayments don't add up to what has been repaid
     * (loanAmount − balance) — e.g. payments made before the record existed.
     */
    private boolean incomplete;
    private List<Row> rows;

    @Getter
    @Builder
    public static class Row {
        private Long id;
        /** yyyy-MM */
        private String month;
        private LocalDate paymentDate;
        /** Paid amount in this repayment. */
        private Double amount;
        /** Total paid so far, including this repayment. */
        private Double totalPaid;
        /** Balance left after this repayment. */
        private Double balanceAfter;
        /** PAYROLL, SETTLEMENT or MANUAL. */
        private String source;
        private String reference;
    }
}
