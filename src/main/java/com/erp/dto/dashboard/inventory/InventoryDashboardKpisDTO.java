package com.erp.dto.dashboard.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDashboardKpisDTO {

    private long distinctSkuCount;
    private long totalQuantityOnHand;
    private long totalAvailable;
    private long totalReserved;
    private long totalOnOrder;
    private long lowStockCount;
    private long openSalesQuotations;
    private long openPurchaseOrders;
    private long goodsReceiptsAwaitingInspection;
    private long goodsReceiptsReadyToReceive;
}
