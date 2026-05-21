package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ItemResponseDTO {

    private Long id;
    private String sku;
    private String name;
    private String category;
    private String subCategory;
    private String brand;

    private String type;
    private String description;
    private String unitMeasure;
    private String barcode;
    private String serialNo;
    private String location;

    private Integer quantity;
    private Integer available;
    private Integer reserved;
    private Integer minimum;
    private Integer maximum;
    private Integer reorderLevel;

    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal unitSale;
    private String imageUrl;

    private String status;

    private Instant createdAt;
    private Instant updatedAt;
    private Long warehouse_id;
    private String warehouse_location;
    private String warehouse_name;
}
