package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionDTO {
    private Long companyId;
    private String transactionType;
    //    private String fiscalType;
    private LocalDate transactionDate;
    private BigDecimal amount;
    //    private String debitAccount;
    private Long creditAccount;
    private Long debitAccount;
    //    private String itemCode;
    private String invoiceId;
    private String paymentId;
    private String transactionDescription;
}
