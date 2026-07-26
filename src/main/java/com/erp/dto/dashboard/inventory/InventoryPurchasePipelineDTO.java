package com.erp.dto.dashboard.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPurchasePipelineDTO {

    private long requisitionsSubmitted;
    private long purchaseOrdersDraft;
    private long purchaseOrdersApproved;
    private long purchaseOrdersConfirmed;
    private long goodsReceiptsPendingInspection;
    private long goodsReceiptsReadyToReceive;
}
