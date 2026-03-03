package com.erp.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateReconciliationRequest {

    @NotNull
    private Long accountId;

    @NotNull
    private BigDecimal amount;

    private String resource;
    private String reason;
}