package com.erp.service.dashboard;

import com.erp.domain.purchase.GoodsReceiptStatus;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisitionStatus;
import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.dto.dashboard.inventory.InventoryDashboardAlertDTO;
import com.erp.dto.dashboard.inventory.InventoryDashboardKpisDTO;
import com.erp.dto.dashboard.inventory.InventoryDashboardResponseDTO;
import com.erp.dto.dashboard.inventory.InventoryPurchasePipelineDTO;
import com.erp.dto.dashboard.inventory.InventorySalesPipelineDTO;
import com.erp.dto.inventory.InventoryLowStockItemDTO;
import com.erp.dto.inventory.InventoryWarehouseBreakdownDTO;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.repo.sales.ShipmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Company-scoped inventory / sales / purchase ops dashboard.
 * Stock figures come from {@link ItemWarehouseStock}; open commitments use current PO statuses.
 */
@Service
@Transactional(readOnly = true)
public class InventoryDashboardService {

    private static final int LOW_STOCK_SAMPLE = 10;

    private final AuthContext auth;
    private final ItemWarehouseStockRepository stockRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseRequisitionRepository purchaseRequisitionRepo;
    private final GoodsReceiptRepository goodsReceiptRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final ShipmentRepository shipmentRepo;

    public InventoryDashboardService(
            AuthContext auth,
            ItemWarehouseStockRepository stockRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            PurchaseRequisitionRepository purchaseRequisitionRepo,
            GoodsReceiptRepository goodsReceiptRepo,
            SalesOrderRepository salesOrderRepo,
            ShipmentRepository shipmentRepo
    ) {
        this.auth = auth;
        this.stockRepo = stockRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseRequisitionRepo = purchaseRequisitionRepo;
        this.goodsReceiptRepo = goodsReceiptRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.shipmentRepo = shipmentRepo;
    }

    public InventoryDashboardResponseDTO build() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User is not associated with a company");
        }

        LocalDate today = LocalDate.now();
        Instant startOfMonth = today.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        long distinctSkus = stockRepo.countDistinctItemsForReport(companyId, null, null);
        Object[] totalsRow = normalizeRow(stockRepo.sumTotalsForReport(companyId, null, null));
        long onHand = toLong(valueAt(totalsRow, 0));
        // Available / reserved must share the same ledger: IWS reserved.
        // Confirmed SO qty is already consumed from on-hand — counting it as
        // "reserved" made Available ≈ On Hand while Reserved looked inflated.
        long totalReserved = toLong(valueAt(totalsRow, 1));
        long available = toLong(valueAt(totalsRow, 2));

        List<PurchaseOrderStatus> openOrderStatuses = List.of(
                PurchaseOrderStatus.APPROVED,
                PurchaseOrderStatus.CONFIRMED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED
        );
        Long rawOnOrder = purchaseOrderRepo.sumOnOrderQuantity(companyId, openOrderStatuses);
        long totalOnOrder = rawOnOrder != null ? rawOnOrder : 0L;

        long lowStockCount = stockRepo.countLowStockLinesForReport(companyId, null, null);
        long openSalesQuotations = salesOrderRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, "QUOTATION");
        long openPurchaseOrders = purchaseOrderRepo.countByCompanyIdAndArchivedFalseAndStatusIn(
                companyId, List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED));
        long grAwaitingInspection = goodsReceiptRepo.countByCompany_IdAndStatusAndArchivedFalse(
                companyId, GoodsReceiptStatus.PENDING_INSPECTION);
        long grReadyToReceive = goodsReceiptRepo.countAwaitingStockReceive(companyId);

        InventoryDashboardKpisDTO kpis = InventoryDashboardKpisDTO.builder()
                .distinctSkuCount(distinctSkus)
                .totalQuantityOnHand(onHand)
                .totalAvailable(available)
                .totalReserved(totalReserved)
                .totalOnOrder(totalOnOrder)
                .lowStockCount(lowStockCount)
                .openSalesQuotations(openSalesQuotations)
                .openPurchaseOrders(openPurchaseOrders)
                .goodsReceiptsAwaitingInspection(grAwaitingInspection)
                .goodsReceiptsReadyToReceive(grReadyToReceive)
                .build();

        List<InventoryWarehouseBreakdownDTO> stockByWarehouse = new ArrayList<>();
        for (Object[] row : stockRepo.aggregateByWarehouse(companyId, null, null)) {
            stockByWarehouse.add(InventoryWarehouseBreakdownDTO.builder()
                    .warehouseId(((Number) row[0]).longValue())
                    .warehouseName((String) row[1])
                    .onHand(toLong(row[2]))
                    .reserved(toLong(row[3]))
                    .available(toLong(row[4]))
                    .valueAtCost(toBigDecimal(row[5]))
                    .build());
        }

        List<InventoryLowStockItemDTO> lowStockItems = new ArrayList<>();
        for (ItemWarehouseStock iws : stockRepo.findLowStockLinesForReport(
                companyId, null, null, PageRequest.of(0, LOW_STOCK_SAMPLE))) {
            var i = iws.getItem();
            var w = iws.getWarehouse();
            lowStockItems.add(InventoryLowStockItemDTO.builder()
                    .itemId(i.getId())
                    .sku(i.getSku())
                    .name(i.getName())
                    .warehouseId(w.getId())
                    .warehouseName(w.getName())
                    .available(iws.available())
                    .reorderLevel(i.getReorderLevel())
                    .build());
        }

        long soConfirmed = salesOrderRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, "CONFIRMED");
        long inTransit = shipmentRepo.countActiveByCompanyIdAndStatus(companyId, "IN_TRANSIT")
                + shipmentRepo.countActiveByCompanyIdAndStatus(companyId, "DISPATCHED")
                + shipmentRepo.countActiveByCompanyIdAndStatus(companyId, "OUT_FOR_DELIVERY");
        long deliveredThisMonth = shipmentRepo.countDeliveredSince(companyId, startOfMonth);

        InventorySalesPipelineDTO salesPipeline = InventorySalesPipelineDTO.builder()
                .quotations(openSalesQuotations)
                .confirmed(soConfirmed)
                .shipmentsInTransit(inTransit)
                .deliveredThisMonth(deliveredThisMonth)
                .build();

        long poDraft = purchaseOrderRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, PurchaseOrderStatus.DRAFT);
        long poApproved = purchaseOrderRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, PurchaseOrderStatus.APPROVED);
        long poConfirmed = purchaseOrderRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, PurchaseOrderStatus.CONFIRMED);
        long prSubmitted = purchaseRequisitionRepo.countByCompanyIdAndArchivedFalseAndStatus(
                companyId, PurchaseRequisitionStatus.SUBMITTED);

        InventoryPurchasePipelineDTO purchasePipeline = InventoryPurchasePipelineDTO.builder()
                .requisitionsSubmitted(prSubmitted)
                .purchaseOrdersDraft(poDraft)
                .purchaseOrdersApproved(poApproved)
                .purchaseOrdersConfirmed(poConfirmed)
                .goodsReceiptsPendingInspection(grAwaitingInspection)
                .goodsReceiptsReadyToReceive(grReadyToReceive)
                .build();

        List<InventoryDashboardAlertDTO> alerts = new ArrayList<>();
        if (lowStockCount > 0) {
            alerts.add(InventoryDashboardAlertDTO.builder()
                    .type("LOW_STOCK")
                    .message("Warehouse stock lines at or below reorder level")
                    .count(lowStockCount)
                    .amount(null)
                    .build());
        }
        if (grAwaitingInspection > 0) {
            alerts.add(InventoryDashboardAlertDTO.builder()
                    .type("AWAITING_INSPECTION")
                    .message("Goods receipts awaiting inspection")
                    .count(grAwaitingInspection)
                    .amount(null)
                    .build());
        }
        if (poDraft > 0) {
            alerts.add(InventoryDashboardAlertDTO.builder()
                    .type("PO_AWAITING_APPROVE")
                    .message("Purchase orders awaiting approval")
                    .count(poDraft)
                    .amount(null)
                    .build());
        }
        if (poApproved > 0) {
            alerts.add(InventoryDashboardAlertDTO.builder()
                    .type("PO_AWAITING_RELEASE")
                    .message("Purchase orders approved, awaiting release to supplier")
                    .count(poApproved)
                    .amount(null)
                    .build());
        }

        return InventoryDashboardResponseDTO.builder()
                .kpis(kpis)
                .stockByWarehouse(stockByWarehouse)
                .lowStockItems(lowStockItems)
                .salesPipeline(salesPipeline)
                .purchasePipeline(purchasePipeline)
                .alerts(alerts)
                .generatedAt(Instant.now())
                .build();
    }

    private static Object[] normalizeRow(Object[] row) {
        if (row == null) {
            return new Object[0];
        }
        if (row.length == 1 && row[0] instanceof Object[] nested) {
            return nested;
        }
        return row;
    }

    private static Object valueAt(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length) {
            return null;
        }
        return row[index];
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
