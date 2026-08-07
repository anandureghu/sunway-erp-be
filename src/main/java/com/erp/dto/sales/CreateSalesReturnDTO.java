package com.erp.dto.sales;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateSalesReturnDTO {

    @NotNull
    private Long salesOrderId;

    private String reason;

    /** When true (default), returned qty is restocked into the line warehouse. */
    private Boolean restock = true;

    @NotEmpty
    private List<Line> items;

    @Data
    public static class Line {
        /** Sales order line id. Prefer this when available. */
        private Long salesOrderItemId;

        /** Fallback match when salesOrderItemId is omitted. */
        private Long itemId;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
