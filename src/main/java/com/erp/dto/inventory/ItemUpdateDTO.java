package com.erp.dto.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemUpdateDTO {

    private String name;
    private String category;
    private String subCategory;
    private String brand;
    private String location;

    private Integer quantity;
    private Integer minimum;
    private Integer maximum;

    private BigDecimal costPrice;
    private BigDecimal sellingPrice;

    private String status;
    private String imageUrl;
    private String description;
}
