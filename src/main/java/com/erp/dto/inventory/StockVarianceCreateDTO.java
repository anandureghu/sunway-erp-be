package com.erp.dto.inventory;

import lombok.Data;

@Data
public class StockVarianceCreateDTO {
    private Long itemId;
    private Long warehouseId;
    private Long toWarehouseId;
    private String varianceType;
    private String adjustmentMode;
    private Integer adjustmentQuantity;
    private Integer newQuantity;
    private Integer transferQuantity;
    private String reason;
    private String notes;
    private String varianceDate;
}
