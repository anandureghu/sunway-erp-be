package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderUpdateDTO {

    private LocalDate orderDate;
    private List<SalesOrderItemDTO> items;
}
