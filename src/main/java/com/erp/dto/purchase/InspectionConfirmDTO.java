package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionConfirmDTO {
    private List<Line> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long goodsReceiptItemId;
        private Integer acceptedQty;
        private Integer rejectedQty;
        private String remarks;
    }
}
