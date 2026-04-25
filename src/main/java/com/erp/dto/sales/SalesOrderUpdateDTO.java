package com.erp.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
    @NotEmpty(message = "Sales order must have at least one item")
    @Valid
    private List<SalesOrderItemDTO> items;
}
