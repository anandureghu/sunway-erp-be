package com.erp.dto.finance.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceAgingBucketsDTO {

    private BigDecimal current;
    private BigDecimal d1To30;
    private BigDecimal d31To60;
    private BigDecimal d61To90;
    private BigDecimal d90Plus;

    private long currentCount;
    private long d1To30Count;
    private long d31To60Count;
    private long d61To90Count;
    private long d90PlusCount;
}
