package com.erp.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SalesOrderResponseDTO {

    private Long id;
    private String orderNumber;
    private Long customerId;
    private LocalDate orderDate;
    private String status;
    private BigDecimal totalAmount;
    private List<SalesOrderItemResponseDTO> items;
}
