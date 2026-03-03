package com.erp.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateJournalEntryRequest {

    private Long creditAccountId;

    private Long debitAccountId;

    @NotNull
    private BigDecimal amount;

    private String source;
    private String description;
}
