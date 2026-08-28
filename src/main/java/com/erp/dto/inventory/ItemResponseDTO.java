package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    private LocalDate dateReceived;
    private LocalDate expiryDate;
    private String location;

    private Integer quantity;
    private Integer available;
    private Integer reserved;
    /** Remaining qty on open purchase orders (not yet received/rejected). */
    private Integer quantityOnOrder;
    private Integer minimum;
    private Integer maximum;
    private Integer reorderLevel;

    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    /** Undiscounted retail price; sellingPrice may be lower when discounted. */
    private BigDecimal listPrice;
    private BigDecimal unitSale;
    private String imageUrl;

    private String status;

    private Boolean archived;

    /** JSON object of unmapped import columns (header → value). */
    private String metadata;

    private Instant createdAt;
    private Instant updatedAt;
    private Long warehouse_id;
    private String warehouse_location;
    private String warehouse_name;
}
