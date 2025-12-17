package com.erp.dto.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemCreateDTO {

    private String sku;
    private String name;
    private String type;
    private String category;
    private String subCategory;
    private String brand;
    private String location;

    private Integer quantity;
    private Integer minimum;
    private Integer maximum;

    private String barcode;
    private String serialNo;

    private BigDecimal costPrice;
    private BigDecimal sellingPrice;

    private String unitMeasure;
    private Integer reorderLevel;
    private String status;
    private String imageUrl;
    private String description;
}
