package com.erp.service.inventory;

import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.dto.inventory.*;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InventoryReportService {

    private static final int TOP_STOCK_LINES = 10;
    private static final int LOW_STOCK_SAMPLE = 25;

    private final ItemWarehouseStockRepository stockRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final AuthContext auth;
    private final StockBatchService stockBatchService;

    public InventoryReportService(
            ItemWarehouseStockRepository stockRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            AuthContext auth,
            StockBatchService stockBatchService
    ) {
        this.stockRepo = stockRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.auth = auth;
        this.stockBatchService = stockBatchService;
    }

    public InventoryReportSummaryDTO buildSummary(Long warehouseId, String category) {
        Long companyId = auth.getCurrentCompanyId();
        String cat = normalizeCategory(category);

        long distinctSkus = stockRepo.countDistinctItemsForReport(companyId, warehouseId, cat);

        Object[] totalsRow = normalizeRow(stockRepo.sumTotalsForReport(companyId, warehouseId, cat));
        long totalOnHand = toLong(valueAt(totalsRow, 0));
        long totalReserved = toLong(valueAt(totalsRow, 1));
        long totalAvailable = toLong(valueAt(totalsRow, 2));
        BigDecimal valueCost = toBigDecimal(valueAt(totalsRow, 3));
        BigDecimal batchValueCost = stockBatchService.sumBatchValueForReport(companyId, warehouseId, cat);
        boolean useBatchValuation = batchValueCost.compareTo(BigDecimal.ZERO) > 0;
        if (useBatchValuation) {
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
        if (useBatchValuation) {
            // Keep qty from stock rows; overlay cost from batch layers so charts match the KPI.
            Map<Long, BigDecimal> batchValueByWh = new HashMap<>();
            Map<Long, String> whNames = new HashMap<>();
            for (Object[] row : stockBatchService.aggregateBatchValueByWarehouse(
                    companyId, warehouseId, cat)) {
                Long wid = ((Number) row[0]).longValue();
                whNames.put(wid, (String) row[1]);
                batchValueByWh.put(wid, toBigDecimal(row[3]));
            }
            for (Object[] row : stockRepo.aggregateByWarehouse(companyId, warehouseId, cat)) {
                Long wid = ((Number) row[0]).longValue();
                byWh.add(InventoryWarehouseBreakdownDTO.builder()
                        .warehouseId(wid)
                        .warehouseName((String) row[1])
                        .onHand(toLong(row[2]))
                        .reserved(toLong(row[3]))
                        .available(toLong(row[4]))
                        .valueAtCost(batchValueByWh.getOrDefault(wid, BigDecimal.ZERO))
                        .build());
            }
            // Include warehouses that have batch value but no stock-row aggregate (edge case).
            for (Map.Entry<Long, BigDecimal> e : batchValueByWh.entrySet()) {
                boolean present = byWh.stream().anyMatch(w -> w.getWarehouseId().equals(e.getKey()));
                if (!present) {
                    byWh.add(InventoryWarehouseBreakdownDTO.builder()
                            .warehouseId(e.getKey())
                            .warehouseName(whNames.get(e.getKey()))
                            .onHand(0L)
                            .reserved(0L)
                            .available(0L)
                            .valueAtCost(e.getValue())
                            .build());
                }
            }
        } else {
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
        }

        List<InventoryCategoryBreakdownDTO> byCat = new ArrayList<>();
        if (useBatchValuation) {
            for (Object[] row : stockBatchService.aggregateBatchValueByCategory(
                    companyId, warehouseId, cat)) {
                byCat.add(InventoryCategoryBreakdownDTO.builder()
                        .category((String) row[0])
                        .skuCount(toLong(row[1]))
                        .onHand(toLong(row[2]))
                        .valueAtCost(toBigDecimal(row[3]))
                        .build());
            }
        } else {
            for (Object[] row : stockRepo.aggregateByCategory(companyId, warehouseId, cat)) {
                byCat.add(InventoryCategoryBreakdownDTO.builder()
                        .category((String) row[0])
                        .skuCount(toLong(row[1]))
                        .onHand(toLong(row[2]))
                        .valueAtCost(toBigDecimal(row[3]))
                        .build());
            }
        }

        List<InventoryTopStockLineDTO> topDtos = new ArrayList<>();
        if (useBatchValuation) {
            for (Object[] row : stockBatchService.topBatchLinesByValue(
                    companyId, warehouseId, cat, TOP_STOCK_LINES)) {
                topDtos.add(InventoryTopStockLineDTO.builder()
                        .itemId(((Number) row[0]).longValue())
                        .sku((String) row[1])
                        .name((String) row[2])
                        .warehouseId(((Number) row[3]).longValue())
                        .warehouseName((String) row[4])
                        .quantityOnHand((int) toLong(row[5]))
                        .valueAtCost(toBigDecimal(row[6]))
                        .build());
            }
        } else {
            List<ItemWarehouseStock> topLines = stockRepo.findStockLinesOrderByValueDesc(
                    companyId, warehouseId, cat, PageRequest.of(0, TOP_STOCK_LINES));
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
            int page,
            int size,
            boolean archived
    ) {
        return stockBatchService.buildMovementReport(
                auth.getCurrentCompanyId(),
                warehouseId,
                itemId,
                page,
                size,
                archived
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
