package com.erp.dto.sales;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SalesOrderUpdateDTO {

    private LocalDate orderDate;
    private List<SalesOrderItemDTO> items;
}
