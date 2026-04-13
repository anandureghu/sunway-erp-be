package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateTransactionDTO {
    private String transactionType;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String transactionDescription;
}