package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchHistoryPointDTO {
    private String period;
    private int receiveQty;
    private int issueQty;
    private BigDecimal receiveValue;
    private BigDecimal issueValue;
}
