package com.erp.dto.inventory.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * STOCK_SHEET_REPORTS — Item Summary sheet payload.
 * Delete with StockSheetReportService when those report pages are retired.
 */
@Getter
@Builder
public class ItemSummaryReportDTO {
    private Long itemId;
    private String sku;
    private String name;
    private String description;
    private String category;
    private String subCategory;
    private String brand;
    private String manufacturerPartNumber;
    private String model;
    private String barcode;
    private String serialNo;
    private String type;
    private String status;
    private String unitMeasure;
    private Integer reorderLevel;
    private Integer reorderQty;
    private Integer leadTimeDays;
    private Integer minimum;
    private Integer maximum;
    private String hsnCode;
    private Boolean vatApplicable;
    private String criticality;
    private String preferredVendorName;
    private String supplierPartNo;
    private String remarks;
    private String companyName;
    private Instant generatedAt;
    private Instant createdAt;
    private Instant updatedAt;

    private int onHand;
    private int reserved;
    private int available;
    private BigDecimal avgCost;
    private BigDecimal lastPurchaseCost;
    private BigDecimal listPrice;
    private BigDecimal sellingPrice;
    private BigDecimal stockValue;
    private String abc;
    private String tracking;
    private Integer lastIssueDays;
    private boolean expiringBatch;

    private List<WarehouseStock> warehouses;
    private List<BatchLine> batches;
    private List<MovementLine> recentMovements;
    private List<MovementTotal> movementTotals;

    @Getter
    @Builder
    public static class WarehouseStock {
        private Long warehouseId;
        private String warehouseName;
        private String warehouseType;
        private int onHand;
        private int reserved;
        private int available;
        private Integer reorderLevel;
        private Integer maximum;
        private boolean belowReorder;
    }

    @Getter
    @Builder
    public static class BatchLine {
        private String batchNo;
        private LocalDate receivedAt;
        private LocalDate expiryDate;
        private int quantityOnHand;
        private BigDecimal unitCost;
        private String warehouseName;
        /** ok | warn | danger relative to expiry. */
        private String expiryTone;
    }

    @Getter
    @Builder
    public static class MovementLine {
        private Instant at;
        private String movementType;
        private int quantity;
        private String batchNo;
        private String warehouseName;
        private String referenceType;
        private Long referenceId;
    }

    @Getter
    @Builder
    public static class MovementTotal {
        private String label;
        private int quantity;
        private String note;
    }
}
