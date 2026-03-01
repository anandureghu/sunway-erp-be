package com.erp.dto.hr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AllowanceRequestDTO {

    @NotNull(message = "Allowance type is required")
    private Long allowanceTypeId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private String note;
}