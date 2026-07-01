package com.erp.dto.inventory;

import com.erp.domain.inventory.StockBatchMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchMovementResponseDTO {
    private Long id;
    private Long batchId;
    private String batchNo;
    private Long itemId;
    private String itemSku;
    private String itemName;
    private Long warehouseId;
    private String warehouseName;
    private StockBatchMovementType movementType;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal lineValue;
    private String referenceType;
    private Long referenceId;
    private Instant createdAt;
}
