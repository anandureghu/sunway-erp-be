package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPostingDTO {
    private List<Line> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long goodsReceiptItemId;
        private Long warehouseId;
        private String batchNo;
        private String lotNo;
        private BigDecimal unitCost;
        private LocalDate expiryDate;
    }
}
