package com.erp.service.inventory;

import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.dto.inventory.*;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InventoryReportService {

    private static final int TOP_STOCK_LINES = 10;
    private static final int LOW_STOCK_SAMPLE = 25;

    private final ItemWarehouseStockRepository stockRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final AuthContext auth;
    private final StockBatchService stockBatchService;

    public InventoryReportService(
            ItemWarehouseStockRepository stockRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            SalesOrderRepository salesOrderRepo,
            AuthContext auth,
            StockBatchService stockBatchService
    ) {
        this.stockRepo = stockRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.auth = auth;
        this.stockBatchService = stockBatchService;
    }

    public InventoryReportSummaryDTO buildSummary(Long warehouseId, String category) {
        Long companyId = auth.getCurrentCompanyId();
        String cat = normalizeCategory(category);

        long distinctSkus = stockRepo.countDistinctItemsForReport(companyId, warehouseId, cat);

        Object[] totalsRow = normalizeRow(stockRepo.sumTotalsForReport(companyId, warehouseId, cat));
        long totalOnHand = toLong(valueAt(totalsRow, 0));
        long totalAvailable = toLong(valueAt(totalsRow, 2));
        BigDecimal valueCost = toBigDecimal(valueAt(totalsRow, 3));
        BigDecimal batchValueCost = stockBatchService.sumBatchValueForReport(companyId, warehouseId, cat);
        if (batchValueCost.compareTo(BigDecimal.ZERO) > 0) {
            valueCost = batchValueCost;
        }
        BigDecimal valueSelling = toBigDecimal(valueAt(totalsRow, 4));

        List<PurchaseOrderStatus> openOrderStatuses = List.of(
                PurchaseOrderStatus.APPROVED,
                PurchaseOrderStatus.CONFIRMED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED
        );
        Long rawOnOrder = purchaseOrderRepo.sumOnOrderQuantity(companyId, openOrderStatuses);
        long totalOnOrder = rawOnOrder != null ? rawOnOrder : 0L;

        Long rawOnReserve = salesOrderRepo.sumConfirmedOrderQuantity(companyId);
        long totalReserved = rawOnReserve != null ? rawOnReserve : 0L;

        InventoryReportTotalsDTO totals = InventoryReportTotalsDTO.builder()
                .distinctSkuCount(distinctSkus)
                .totalQuantityOnHand(totalOnHand)
                .totalReserved(totalReserved)
                .totalAvailable(totalAvailable)
                .stockValueAtCost(valueCost)
                .stockValueAtSelling(valueSelling)
                .totalOnOrder(totalOnOrder)
                .build();

        List<InventoryWarehouseBreakdownDTO> byWh = new ArrayList<>();
        for (Object[] row : stockRepo.aggregateByWarehouse(companyId, warehouseId, cat)) {
            byWh.add(InventoryWarehouseBreakdownDTO.builder()
                    .warehouseId(((Number) row[0]).longValue())
                    .warehouseName((String) row[1])
                    .onHand(toLong(row[2]))
                    .reserved(toLong(row[3]))
                    .available(toLong(row[4]))
                    .valueAtCost(toBigDecimal(row[5]))
                    .build());
        }

        List<InventoryCategoryBreakdownDTO> byCat = new ArrayList<>();
        for (Object[] row : stockRepo.aggregateByCategory(companyId, warehouseId, cat)) {
            byCat.add(InventoryCategoryBreakdownDTO.builder()
                    .category((String) row[0])
                    .skuCount(toLong(row[1]))
                    .onHand(toLong(row[2]))
                    .valueAtCost(toBigDecimal(row[3]))
                    .build());
        }

        List<ItemWarehouseStock> topLines = stockRepo.findStockLinesOrderByValueDesc(
                companyId, warehouseId, cat, PageRequest.of(0, TOP_STOCK_LINES));
        List<InventoryTopStockLineDTO> topDtos = new ArrayList<>();
        for (ItemWarehouseStock iws : topLines) {
            var i = iws.getItem();
            var w = iws.getWarehouse();
            BigDecimal lineValue = BigDecimal.valueOf(nz(iws.getQuantityOnHand()))
                    .multiply(i.getCostPrice() != null ? i.getCostPrice() : BigDecimal.ZERO);
            topDtos.add(InventoryTopStockLineDTO.builder()
                    .itemId(i.getId())
                    .sku(i.getSku())
                    .name(i.getName())
                    .warehouseId(w.getId())
                    .warehouseName(w.getName())
                    .quantityOnHand(nz(iws.getQuantityOnHand()))
                    .valueAtCost(lineValue)
                    .build());
        }

        long lowCount = stockRepo.countLowStockLinesForReport(companyId, warehouseId, cat);
        List<ItemWarehouseStock> lowLines = stockRepo.findLowStockLinesForReport(
                companyId, warehouseId, cat, PageRequest.of(0, LOW_STOCK_SAMPLE));
        List<InventoryLowStockItemDTO> lowDtos = new ArrayList<>();
        for (ItemWarehouseStock iws : lowLines) {
            var i = iws.getItem();
            var w = iws.getWarehouse();
            lowDtos.add(InventoryLowStockItemDTO.builder()
                    .itemId(i.getId())
                    .sku(i.getSku())
                    .name(i.getName())
                    .warehouseId(w.getId())
                    .warehouseName(w.getName())
                    .available(iws.available())
                    .reorderLevel(i.getReorderLevel())
                    .build());
        }

        return InventoryReportSummaryDTO.builder()
                .totals(totals)
                .byWarehouse(byWh)
                .byCategory(byCat)
                .topStockLinesByValue(topDtos)
                .lowStockItemCount(lowCount)
                .lowStockItems(lowDtos)
                .generatedAt(Instant.now())
                .build();
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return null;
        }
        String t = category.trim();
        return t.isEmpty() ? null : t;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
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

    public StockBatchReportDTO buildBatchReport(Long warehouseId, Long itemId, String batchNo) {
        return stockBatchService.buildBatchReport(
                auth.getCurrentCompanyId(),
                warehouseId,
                itemId,
                batchNo
        );
    }

    public com.erp.dto.inventory.StockBatchMovementReportDTO buildBatchMovementReport(
            Long warehouseId,
            Long itemId,
            int limit
    ) {
        return stockBatchService.buildMovementReport(
                auth.getCurrentCompanyId(),
                warehouseId,
                itemId,
                limit
        );
    }

    public com.erp.dto.inventory.StockBatchInsightsDTO buildBatchInsights(
            Long warehouseId,
            Long itemId
    ) {
        return stockBatchService.buildInsights(
                auth.getCurrentCompanyId(),
                warehouseId,
                itemId
        );
    }
}
