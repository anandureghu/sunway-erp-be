package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PicklistItemDTO {
    private Long itemId;
    private Integer quantity;
}
