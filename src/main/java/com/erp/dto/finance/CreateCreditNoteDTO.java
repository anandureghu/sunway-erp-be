package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCreditNoteDTO {

    private String invoiceId;
    private BigDecimal amount;
    private String reason;
    private LocalDate creditDate;
}
