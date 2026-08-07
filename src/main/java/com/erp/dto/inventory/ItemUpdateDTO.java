package com.erp.dto.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemUpdateDTO {

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
    private Integer reorderLevel;

    private String barcode;
    private String serialNo;
    private String dateReceived;
    private String expiryDate;
    private String unitMeasure;

    private BigDecimal costPrice;
    private BigDecimal unitSale;
    private BigDecimal sellingPrice;

    private String status;
    private String imageUrl;
    private String description;
    /** JSON object of unmapped import columns (header → value). */
    private String metadata;

    private Long warehouse;
}
