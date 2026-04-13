package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SetInitialBalanceDTO {
    private BigDecimal amount;
}
