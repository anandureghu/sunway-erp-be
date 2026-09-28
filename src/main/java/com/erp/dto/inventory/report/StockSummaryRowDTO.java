package com.erp.dto.inventory.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * STOCK_SHEET_REPORTS — one SKU line on Stock Summary.
 * Delete with StockSheetReportService when those report pages are retired.
 */
@Getter
@Builder
public class StockSummaryRowDTO {
    private Long itemId;
    private String sku;
    private String name;
    private String category;
    private String brand;
    private String unitMeasure;
    /** "Batch" when the item has on-hand batch layers; otherwise "None". */
    private String tracking;
    private int onHand;
    private Integer reorderLevel;
    private BigDecimal avgCost;
    private BigDecimal stockValue;
    /** Days since last SALE/TRANSFER_OUT movement; null if never issued. */
    private Integer lastIssueDays;
    /** A / B / C by stock-value rank across the register. */
    private String abc;
    /** True when any on-hand batch expires within 90 days. */
    private boolean expiringBatch;
    private String status;
}
