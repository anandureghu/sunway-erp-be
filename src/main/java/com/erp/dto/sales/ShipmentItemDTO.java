package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShipmentItemDTO {
    private Long itemId;
    private Integer quantity;
}

