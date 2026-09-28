package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.inventory.StockBatch;
import com.erp.domain.inventory.StockBatchMovement;
import com.erp.domain.inventory.StockBatchMovementType;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.report.ItemSummaryReportDTO;
import com.erp.dto.inventory.report.StockSummaryReportDTO;
import com.erp.dto.inventory.report.StockSummaryRowDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.inventory.StockBatchMovementRepository;
import com.erp.repo.inventory.StockBatchRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * STOCK_SHEET_REPORTS — live data for Stock Summary and Item Summary report sheets.
 * <p>
 * Added for the HR-style inventory report pages (sidebar: Stock Summary / Item Summary).
 * When those pages are removed, delete this service, DTOs under {@code dto.inventory.report},
 * the matching controller mappings, repository helpers annotated STOCK_SHEET_REPORTS,
 * and the frontend stock/item summary modules + routes + sidebar entries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockSheetReportService {

    private static final int NON_MOVING_DAYS = 180;
    private static final int EXPIRY_HORIZON_DAYS = 90;

    private final ItemRepository itemRepo;
    private final ItemWarehouseStockRepository stockRepo;
    private final StockBatchRepository batchRepo;
    private final StockBatchMovementRepository movementRepo;
    private final AuthContext auth;

    public StockSummaryReportDTO stockSummary() {
        Long companyId = auth.getCurrentCompanyId();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<Item> items = itemRepo.findByCompanyIdAndArchivedOrderByCreatedAtDesc(companyId, false);
        List<ItemWarehouseStock> stockRows = stockRepo.findAllByCompanyIdAndArchived(companyId, false);

        Map<Long, Agg> byItem = new HashMap<>();
        Map<Long, WhAgg> byWh = new LinkedHashMap<>();
        String companyName = null;

        for (ItemWarehouseStock iws : stockRows) {
            Item item = iws.getItem();
            Warehouse wh = iws.getWarehouse();
            if (item == null || wh == null) {
                continue;
            }
            if (companyName == null && item.getCompany() != null) {
                companyName = item.getCompany().getCompanyName();
            }
            int onHand = nz(iws.getQuantityOnHand());
            int reserved = nz(iws.getReserved());
            Agg agg = byItem.computeIfAbsent(item.getId(), id -> Agg.from(item));
            agg.onHand += onHand;
            agg.reserved += reserved;

            WhAgg wa = byWh.computeIfAbsent(wh.getId(), id -> new WhAgg(wh.getId(), wh.getName(), wh.getWarehouseType()));
            if (onHand > 0) {
                wa.skuIds.add(item.getId());
            }
            wa.onHand += onHand;
        }

        // Include active items with no stock rows yet (show as zero on hand).
        for (Item item : items) {
            byItem.computeIfAbsent(item.getId(), id -> Agg.from(item));
            if (companyName == null && item.getCompany() != null) {
                companyName = item.getCompany().getCompanyName();
            }
        }

        Map<Long, Instant> lastOutbound = toInstantMap(movementRepo.lastOutboundByItem(companyId));
        Set<Long> expiring = new HashSet<>(batchRepo.itemIdsWithExpiringBatches(
                companyId, today, today.plusDays(EXPIRY_HORIZON_DAYS)));
        Map<Long, BigDecimal> batchAvgCost = weightedAvgCost(batchRepo.batchCostTotalsByItem(companyId));
        Set<Long> batchedItems = batchAvgCost.keySet();

        List<StockSummaryRowDTO> rows = new ArrayList<>();
        for (Agg agg : byItem.values()) {
            BigDecimal avg = batchAvgCost.getOrDefault(agg.itemId,
                    agg.costPrice != null ? agg.costPrice : BigDecimal.ZERO);
            BigDecimal value = avg.multiply(BigDecimal.valueOf(agg.onHand)).setScale(2, RoundingMode.HALF_UP);
            Integer lastIssueDays = daysSince(lastOutbound.get(agg.itemId), now);
            rows.add(StockSummaryRowDTO.builder()
                    .itemId(agg.itemId)
                    .sku(agg.sku)
                    .name(agg.name)
                    .category(blankToUncategorized(agg.category))
                    .brand(agg.brand)
                    .unitMeasure(agg.unitMeasure != null ? agg.unitMeasure : "Piece")
                    .tracking(batchedItems.contains(agg.itemId) ? "Batch" : "None")
                    .onHand(agg.onHand)
                    .reorderLevel(agg.reorderLevel)
                    .avgCost(avg.setScale(4, RoundingMode.HALF_UP))
                    .stockValue(value)
                    .lastIssueDays(lastIssueDays)
                    .abc("C") // filled below
                    .expiringBatch(expiring.contains(agg.itemId))
                    .status(agg.status)
                    .build());
        }

        assignAbc(rows);

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal nonMovingValue = BigDecimal.ZERO;
        long below = 0;
        long zero = 0;
        long nonMoving = 0;
        long expiringCount = 0;
        Map<String, CatAgg> cats = new LinkedHashMap<>();

        for (StockSummaryRowDTO r : rows) {
            totalValue = totalValue.add(nz(r.getStockValue()));
            if (r.getReorderLevel() != null && r.getOnHand() < r.getReorderLevel()) {
                below++;
            }
            if (r.getOnHand() == 0) {
                zero++;
            }
            if (r.getLastIssueDays() != null && r.getLastIssueDays() >= NON_MOVING_DAYS) {
                nonMoving++;
                nonMovingValue = nonMovingValue.add(nz(r.getStockValue()));
            }
            if (r.isExpiringBatch()) {
                expiringCount++;
            }
            CatAgg ca = cats.computeIfAbsent(r.getCategory(), CatAgg::new);
            ca.skuCount++;
            ca.value = ca.value.add(nz(r.getStockValue()));
        }

        // Attribute warehouse value from stock rows × item cost (batch avg when present).
        Map<Long, BigDecimal> itemCost = new HashMap<>();
        for (StockSummaryRowDTO r : rows) {
            itemCost.put(r.getItemId(), r.getAvgCost());
        }
        for (ItemWarehouseStock iws : stockRows) {
            if (iws.getItem() == null || iws.getWarehouse() == null) {
                continue;
            }
            WhAgg wa = byWh.get(iws.getWarehouse().getId());
            if (wa == null) {
                continue;
            }
            BigDecimal cost = itemCost.getOrDefault(iws.getItem().getId(), BigDecimal.ZERO);
            wa.value = wa.value.add(cost.multiply(BigDecimal.valueOf(nz(iws.getQuantityOnHand()))));
        }

        BigDecimal finalTotal = totalValue;
        List<StockSummaryReportDTO.WarehouseBreakdown> warehouses = byWh.values().stream()
                .sorted(Comparator.comparing((WhAgg w) -> w.value).reversed())
                .map(w -> StockSummaryReportDTO.WarehouseBreakdown.builder()
                        .warehouseId(w.id)
                        .warehouseName(w.name)
                        .warehouseType(w.type != null ? w.type : "Store")
                        .skuCount(w.skuIds.size())
                        .valueAtCost(w.value.setScale(2, RoundingMode.HALF_UP))
                        .share(finalTotal.signum() > 0
                                ? w.value.divide(finalTotal, 4, RoundingMode.HALF_UP).doubleValue()
                                : 0d)
                        .build())
                .toList();

        List<StockSummaryReportDTO.CategoryBreakdown> categories = cats.values().stream()
                .sorted(Comparator.comparing((CatAgg c) -> c.value).reversed())
                .map(c -> StockSummaryReportDTO.CategoryBreakdown.builder()
                        .category(c.name)
                        .skuCount(c.skuCount)
                        .valueAtCost(c.value.setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();

        rows.sort(Comparator
                .comparing(StockSummaryRowDTO::getCategory, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StockSummaryRowDTO::getSku, String.CASE_INSENSITIVE_ORDER));

        return StockSummaryReportDTO.builder()
                .companyName(companyName)
                .generatedAt(now)
                .rows(rows)
                .byCategory(categories)
                .byWarehouse(warehouses)
                .belowReorderCount(below)
                .outOfStockCount(zero)
                .nonMovingCount(nonMoving)
                .expiringBatchCount(expiringCount)
                .totalStockValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .nonMovingValue(nonMovingValue.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public ItemSummaryReportDTO itemSummary(Long itemId) {
        Long companyId = auth.getCurrentCompanyId();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        Company company = item.getCompany();
        if (company == null || !Objects.equals(company.getId(), companyId)) {
            throw new NotFoundException("Item not found");
        }

        List<ItemWarehouseStock> stockRows = stockRepo.findByItemId(itemId);
        int onHand = 0;
        int reserved = 0;
        List<ItemSummaryReportDTO.WarehouseStock> warehouses = new ArrayList<>();
        for (ItemWarehouseStock iws : stockRows) {
            Warehouse wh = iws.getWarehouse();
            int q = nz(iws.getQuantityOnHand());
            int r = nz(iws.getReserved());
            onHand += q;
            reserved += r;
            Integer reorder = item.getReorderLevel();
            warehouses.add(ItemSummaryReportDTO.WarehouseStock.builder()
                    .warehouseId(wh != null ? wh.getId() : null)
                    .warehouseName(wh != null ? wh.getName() : "—")
                    .warehouseType(wh != null && wh.getWarehouseType() != null ? wh.getWarehouseType() : "Store")
                    .onHand(q)
                    .reserved(r)
                    .available(iws.available())
                    .reorderLevel(reorder)
                    .maximum(item.getMaximum())
                    .belowReorder(reorder != null && q < reorder)
                    .build());
        }
        warehouses.sort(Comparator.comparing(ItemSummaryReportDTO.WarehouseStock::getOnHand).reversed());

        List<StockBatch> batches = batchRepo.findByItemForCompany(companyId, itemId, null);
        BigDecimal batchValue = BigDecimal.ZERO;
        long batchQty = 0;
        BigDecimal lastPurchase = null;
        LocalDate lastRecv = null;
        List<ItemSummaryReportDTO.BatchLine> batchLines = new ArrayList<>();
        boolean expiring = false;
        for (StockBatch b : batches) {
            if (nz(b.getQuantityOnHand()) <= 0) {
                continue;
            }
            int q = nz(b.getQuantityOnHand());
            batchQty += q;
            BigDecimal unit = b.getUnitCost() != null ? b.getUnitCost() : BigDecimal.ZERO;
            batchValue = batchValue.add(unit.multiply(BigDecimal.valueOf(q)));
            if (lastRecv == null || (b.getReceivedAt() != null && b.getReceivedAt().isAfter(lastRecv))) {
                lastRecv = b.getReceivedAt();
                lastPurchase = unit;
            }
            String tone = "ok";
            if (b.getExpiryDate() != null) {
                long days = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                if (days < 0) {
                    tone = "danger";
                } else if (days <= 90) {
                    tone = "warn";
                    if (days <= EXPIRY_HORIZON_DAYS) {
                        expiring = true;
                    }
                }
            }
            batchLines.add(ItemSummaryReportDTO.BatchLine.builder()
                    .batchNo(b.getBatchNo())
                    .receivedAt(b.getReceivedAt())
                    .expiryDate(b.getExpiryDate())
                    .quantityOnHand(q)
                    .unitCost(unit)
                    .warehouseName(b.getWarehouse() != null ? b.getWarehouse().getName() : "—")
                    .expiryTone(tone)
                    .build());
        }

        BigDecimal avgCost;
        if (batchQty > 0) {
            avgCost = batchValue.divide(BigDecimal.valueOf(batchQty), 4, RoundingMode.HALF_UP);
        } else {
            avgCost = item.getCostPrice() != null ? item.getCostPrice() : BigDecimal.ZERO;
        }
        if (lastPurchase == null) {
            lastPurchase = avgCost;
        }

        Map<Long, Instant> lastOutbound = toInstantMap(movementRepo.lastOutboundByItem(companyId));
        Integer lastIssueDays = daysSince(lastOutbound.get(itemId), now);

        List<StockBatchMovement> recent = movementRepo.findRecentForItem(
                companyId, itemId, PageRequest.of(0, 40));
        List<ItemSummaryReportDTO.MovementLine> movementLines = recent.stream()
                .map(m -> ItemSummaryReportDTO.MovementLine.builder()
                        .at(m.getCreatedAt())
                        .movementType(m.getMovementType() != null ? m.getMovementType().name() : null)
                        .quantity(nz(m.getQuantity()))
                        .batchNo(m.getStockBatch() != null ? m.getStockBatch().getBatchNo() : null)
                        .warehouseName(m.getStockBatch() != null && m.getStockBatch().getWarehouse() != null
                                ? m.getStockBatch().getWarehouse().getName() : null)
                        .referenceType(m.getReferenceType())
                        .referenceId(m.getReferenceId())
                        .build())
                .toList();

        int receipts = 0;
        int issues = 0;
        int adjustments = 0;
        int transfers = 0;
        for (StockBatchMovement m : recent) {
            int q = nz(m.getQuantity());
            StockBatchMovementType t = m.getMovementType();
            if (t == null) {
                continue;
            }
            switch (t) {
                case RECEIVE, RESTORE, TRANSFER_IN -> receipts += Math.abs(q);
                case SALE -> issues += Math.abs(q);
                case ADJUSTMENT -> adjustments += q;
                case TRANSFER_OUT -> transfers += Math.abs(q);
            }
        }
        List<ItemSummaryReportDTO.MovementTotal> totals = List.of(
                ItemSummaryReportDTO.MovementTotal.builder().label("Receipts").quantity(receipts).note("Recent").build(),
                ItemSummaryReportDTO.MovementTotal.builder().label("Issues / sales").quantity(-issues).note("Recent").build(),
                ItemSummaryReportDTO.MovementTotal.builder().label("Transfers out").quantity(-transfers).note("Recent").build(),
                ItemSummaryReportDTO.MovementTotal.builder().label("Adjustments").quantity(adjustments).note("Net").build(),
                ItemSummaryReportDTO.MovementTotal.builder().label("On hand").quantity(onHand).note("Current").build()
        );

        BigDecimal thisValue = avgCost.multiply(BigDecimal.valueOf(onHand));
        String abc = abcForItem(companyId, itemId, thisValue);

        return ItemSummaryReportDTO.builder()
                .itemId(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .category(blankToUncategorized(item.getCategory()))
                .subCategory(item.getSubCategory())
                .brand(item.getBrand())
                .manufacturerPartNumber(item.getManufacturerPartNumber())
                .model(item.getModel())
                .barcode(item.getBarcode())
                .serialNo(item.getSerialNo())
                .type(item.getType() != null ? item.getType() : "Stock item")
                .status(item.getStatus())
                .unitMeasure(item.getUnitMeasure() != null ? item.getUnitMeasure() : "Piece")
                .reorderLevel(item.getReorderLevel())
                .reorderQty(item.getReorderQty())
                .leadTimeDays(item.getLeadTimeDays())
                .minimum(item.getMinimum())
                .maximum(item.getMaximum())
                .hsnCode(item.getHsnCode())
                .vatApplicable(item.getVatApplicable())
                .criticality(item.getCriticality())
                .preferredVendorName(item.getPreferredVendor() != null
                        ? item.getPreferredVendor().getVendorName() : null)
                .supplierPartNo(item.getSupplierPartNo())
                .remarks(item.getRemarks())
                .companyName(company.getCompanyName())
                .generatedAt(now)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .onHand(onHand)
                .reserved(reserved)
                .available(Math.max(0, onHand - reserved))
                .avgCost(avgCost)
                .lastPurchaseCost(lastPurchase)
                .listPrice(item.getListPrice())
                .sellingPrice(item.getSellingPrice())
                .stockValue(avgCost.multiply(BigDecimal.valueOf(onHand)).setScale(2, RoundingMode.HALF_UP))
                .abc(abc)
                .tracking(batchLines.isEmpty() ? "None" : "Batch")
                .lastIssueDays(lastIssueDays)
                .expiringBatch(expiring)
                .warehouses(warehouses)
                .batches(batchLines)
                .recentMovements(movementLines)
                .movementTotals(totals)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Lightweight ABC class for one item without building the full stock-summary DTO. */
    private String abcForItem(Long companyId, Long itemId, BigDecimal thisValue) {
        List<ItemWarehouseStock> stockRows = stockRepo.findAllByCompanyIdAndArchived(companyId, false);
        Map<Long, BigDecimal> batchAvg = weightedAvgCost(batchRepo.batchCostTotalsByItem(companyId));
        Map<Long, Integer> qty = new HashMap<>();
        Map<Long, BigDecimal> itemCostFallback = new HashMap<>();
        for (ItemWarehouseStock iws : stockRows) {
            Item i = iws.getItem();
            if (i == null) {
                continue;
            }
            qty.merge(i.getId(), nz(iws.getQuantityOnHand()), Integer::sum);
            itemCostFallback.putIfAbsent(i.getId(),
                    i.getCostPrice() != null ? i.getCostPrice() : BigDecimal.ZERO);
        }
        List<BigDecimal> values = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qty.entrySet()) {
            BigDecimal cost = batchAvg.getOrDefault(e.getKey(),
                    itemCostFallback.getOrDefault(e.getKey(), BigDecimal.ZERO));
            values.add(cost.multiply(BigDecimal.valueOf(e.getValue())));
        }
        // Ensure the current item is represented even with zero stock rows.
        if (!qty.containsKey(itemId)) {
            values.add(nz(thisValue));
        }
        values.sort(Comparator.reverseOrder());
        int n = values.size();
        if (n == 0) {
            return "C";
        }
        int aCut = Math.max(1, (int) Math.ceil(n * 0.2));
        int bCut = Math.max(aCut, (int) Math.ceil(n * 0.5));
        BigDecimal target = nz(thisValue);
        int rank = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).compareTo(target) <= 0) {
                rank = i;
                break;
            }
            rank = i;
        }
        if (rank < aCut) {
            return "A";
        }
        if (rank < bCut) {
            return "B";
        }
        return "C";
    }

    private static void assignAbc(List<StockSummaryRowDTO> rows) {
        List<StockSummaryRowDTO> ranked = rows.stream()
                .sorted(Comparator.comparing((StockSummaryRowDTO r) -> nz(r.getStockValue())).reversed())
                .toList();
        int n = ranked.size();
        if (n == 0) {
            return;
        }
        int aCut = Math.max(1, (int) Math.ceil(n * 0.2));
        int bCut = Math.max(aCut, (int) Math.ceil(n * 0.5));
        List<StockSummaryRowDTO> rebuilt = new ArrayList<>(rows.size());
        Map<Long, String> abc = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            String cls = i < aCut ? "A" : (i < bCut ? "B" : "C");
            abc.put(ranked.get(i).getItemId(), cls);
        }
        for (StockSummaryRowDTO r : rows) {
            rebuilt.add(StockSummaryRowDTO.builder()
                    .itemId(r.getItemId())
                    .sku(r.getSku())
                    .name(r.getName())
                    .category(r.getCategory())
                    .brand(r.getBrand())
                    .unitMeasure(r.getUnitMeasure())
                    .tracking(r.getTracking())
                    .onHand(r.getOnHand())
                    .reorderLevel(r.getReorderLevel())
                    .avgCost(r.getAvgCost())
                    .stockValue(r.getStockValue())
                    .lastIssueDays(r.getLastIssueDays())
                    .abc(abc.getOrDefault(r.getItemId(), "C"))
                    .expiringBatch(r.isExpiringBatch())
                    .status(r.getStatus())
                    .build());
        }
        rows.clear();
        rows.addAll(rebuilt);
    }

    private static Map<Long, BigDecimal> weightedAvgCost(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }
            long id = ((Number) row[0]).longValue();
            BigDecimal value = toBd(row[1]);
            long qty = ((Number) row[2]).longValue();
            if (qty > 0) {
                map.put(id, value.divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP));
            }
        }
        return map;
    }

    private static Map<Long, Instant> toInstantMap(List<Object[]> rows) {
        Map<Long, Instant> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            map.put(((Number) row[0]).longValue(), (Instant) row[1]);
        }
        return map;
    }

    private static Integer daysSince(Instant at, Instant now) {
        if (at == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(at, now);
    }

    private static String blankToUncategorized(String category) {
        if (category == null || category.isBlank()) {
            return "Uncategorized";
        }
        return category.trim();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toBd(Object o) {
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

    private static final class Agg {
        final Long itemId;
        final String sku;
        final String name;
        final String category;
        final String brand;
        final String unitMeasure;
        final Integer reorderLevel;
        final BigDecimal costPrice;
        final String status;
        int onHand;
        int reserved;

        static Agg from(Item item) {
            return new Agg(
                    item.getId(),
                    item.getSku(),
                    item.getName(),
                    item.getCategory(),
                    item.getBrand(),
                    item.getUnitMeasure(),
                    item.getReorderLevel(),
                    item.getCostPrice(),
                    item.getStatus()
            );
        }

        Agg(Long itemId, String sku, String name, String category, String brand,
            String unitMeasure, Integer reorderLevel, BigDecimal costPrice, String status) {
            this.itemId = itemId;
            this.sku = sku;
            this.name = name;
            this.category = category;
            this.brand = brand;
            this.unitMeasure = unitMeasure;
            this.reorderLevel = reorderLevel;
            this.costPrice = costPrice;
            this.status = status;
        }
    }

    private static final class WhAgg {
        final Long id;
        final String name;
        final String type;
        final Set<Long> skuIds = new HashSet<>();
        int onHand;
        BigDecimal value = BigDecimal.ZERO;

        WhAgg(Long id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    private static final class CatAgg {
        final String name;
        long skuCount;
        BigDecimal value = BigDecimal.ZERO;

        CatAgg(String name) {
            this.name = name;
        }
    }
}
