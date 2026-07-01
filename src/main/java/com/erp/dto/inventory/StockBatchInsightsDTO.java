package com.erp.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchInsightsDTO {
    private int expiringWithin30Days;
    private int expiringWithin60Days;
    private int expiringWithin90Days;
    private BigDecimal valueExpiringWithin30Days;
    private BigDecimal valueExpiringWithin60Days;
    private BigDecimal valueExpiringWithin90Days;
    private List<StockBatchCostLayerDTO> valueByCostLayer;
    private List<StockBatchResponseDTO> expiringSoon;
}
