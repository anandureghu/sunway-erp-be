package com.erp.dto.finance;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateJournalEntryRequest {

    private Long creditAccountId;

    private Long debitAccountId;

    private BigDecimal amount;

    private String source;
    private LocalDate date;
    private String description;
}