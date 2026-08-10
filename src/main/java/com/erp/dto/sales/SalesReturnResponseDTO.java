package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SalesReturnResponseDTO {
    private Long id;
    private String returnNumber;
    private Long salesOrderId;
    private String salesOrderNumber;
    private Long customerId;
    private String customerName;
    private BigDecimal totalAmount;
    private String reason;
    private boolean restock;
    private String status;
    private Long creditNoteId;
    private String creditNoteNumber;
    private String creditNoteStatus;
    private Instant createdAt;
    private List<Line> items;

    @Data
    @Builder
    public static class Line {
        private Long itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
