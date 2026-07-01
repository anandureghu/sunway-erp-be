package com.erp.dto.inventory;

import com.erp.domain.inventory.StockBatchSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchResponseDTO {
    private Long id;
    private Long itemId;
    private String itemSku;
    private String itemName;
    private Long warehouseId;
    private String warehouseName;
    private String batchNo;
    private Integer quantityOnHand;
    private BigDecimal unitCost;
    private BigDecimal lineValue;
    private LocalDate receivedAt;
    private LocalDate expiryDate;
    private StockBatchSourceType sourceType;
    private Long sourceId;
}
