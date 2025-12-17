package com.erp.dto.sales;

import lombok.Data;

@Data
public class SalesOrderItemDTO {
    private Long itemId;
    private Integer quantity;
    private Double unitPrice;
}
