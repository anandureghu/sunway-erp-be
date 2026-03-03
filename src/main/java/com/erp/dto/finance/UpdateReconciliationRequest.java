package com.erp.dto.finance;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateReconciliationRequest {
    private Long accountId;
    private BigDecimal amount;
    private String resource;
    private String reason;
}