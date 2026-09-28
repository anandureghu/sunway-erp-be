package com.erp.dto.inventory.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * STOCK_SHEET_REPORTS — Stock Summary sheet payload.
 * Delete with StockSheetReportService when those report pages are retired.
 */
@Getter
@Builder
public class StockSummaryReportDTO {
    private String companyName;
    private Instant generatedAt;
    private List<StockSummaryRowDTO> rows;
    private List<CategoryBreakdown> byCategory;
    private List<WarehouseBreakdown> byWarehouse;
    private long belowReorderCount;
    private long outOfStockCount;
    private long nonMovingCount;
    private long expiringBatchCount;
    private BigDecimal totalStockValue;
    private BigDecimal nonMovingValue;

    @Getter
    @Builder
    public static class CategoryBreakdown {
        private String category;
        private long skuCount;
        private BigDecimal valueAtCost;
    }

    @Getter
    @Builder
    public static class WarehouseBreakdown {
        private Long warehouseId;
        private String warehouseName;
        private String warehouseType;
        private long skuCount;
        private BigDecimal valueAtCost;
        private double share;
    }
}
