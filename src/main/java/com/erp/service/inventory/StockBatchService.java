package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.*;
import com.erp.dto.inventory.StockBatchCostLayerDTO;
import com.erp.dto.inventory.StockBatchHistoryPointDTO;
import com.erp.dto.inventory.StockBatchInsightsDTO;
import com.erp.dto.inventory.StockBatchMovementReportDTO;
import com.erp.dto.inventory.StockBatchMovementResponseDTO;
import com.erp.dto.inventory.StockBatchReportDTO;
import com.erp.dto.inventory.StockBatchResponseDTO;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.StockBatchMovementRepository;
import com.erp.repo.inventory.StockBatchRepository;
import com.erp.repo.inventory.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class StockBatchService {

    public static final String REF_SALES_ORDER_ITEM = "SALES_ORDER_ITEM";
    public static final String REF_STOCK_VARIANCE = "STOCK_VARIANCE";
    public static final String REF_WAREHOUSE_TRANSFER = "WAREHOUSE_TRANSFER";

    private final StockBatchRepository batchRepo;
    private final StockBatchMovementRepository movementRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final ItemWarehouseStockService stockService;

    public StockBatchService(
            StockBatchRepository batchRepo,
            StockBatchMovementRepository movementRepo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            ItemWarehouseStockService stockService
    ) {
        this.batchRepo = batchRepo;
        this.movementRepo = movementRepo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.stockService = stockService;
    }

    public record BatchConsumptionLine(Long batchId, String batchNo, int quantity, BigDecimal unitCost) {}

    public record ConsumptionResult(
            List<BatchConsumptionLine> lines,
            BigDecimal totalCost,
            BigDecimal weightedUnitCost
    ) {}

    public StockBatch receiveIntoBatch(
            Long itemId,
            Long warehouseId,
            int quantity,
            BigDecimal unitCost,
            String batchNo,
            LocalDate expiryDate,
            StockBatchSourceType sourceType,
            Long sourceId,
            Long companyId
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Receive quantity must be positive");
        }
        BigDecimal cost = normalizeCost(unitCost);
        String resolvedBatchNo = resolveBatchNo(batchNo);

        Item item = loadItem(itemId, companyId);
        Warehouse warehouse = loadWarehouse(warehouseId, companyId);
        // Fall back to the item master sale-by date when the receive payload omits it.
        LocalDate resolvedExpiry = expiryDate != null ? expiryDate : item.getExpiryDate();

        StockBatch batch = batchRepo
                .findByCompanyIdAndItemIdAndWarehouseIdAndBatchNoAndUnitCost(
                        companyId, itemId, warehouseId, resolvedBatchNo, cost)
                .orElseGet(() -> StockBatch.builder()
                        .company(Company.builder().id(companyId).build())
                        .item(item)
                        .warehouse(warehouse)
                        .batchNo(resolvedBatchNo)
                        .quantityOnHand(0)
                        .unitCost(cost)
                        .receivedAt(LocalDate.now())
                        .expiryDate(resolvedExpiry)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .createdAt(Instant.now())
                        .build());

        batch.setQuantityOnHand(nz(batch.getQuantityOnHand()) + quantity);
        if (resolvedExpiry != null) {
            batch.setExpiryDate(resolvedExpiry);
        }
        StockBatch saved = batchRepo.save(batch);

        recordMovement(saved, StockBatchMovementType.RECEIVE, quantity, cost, sourceType.name(), sourceId);
        stockService.addIncomingStock(itemId, warehouseId, quantity, companyId);
        recomputeItemWeightedAverage(itemId);

        return saved;
    }

    public ConsumptionResult consumeFifo(
            Long itemId,
            Long warehouseId,
            int quantity,
            String referenceType,
            Long referenceId,
            Long companyId
    ) {
        return consumeFifo(
                itemId, warehouseId, quantity, referenceType, referenceId, companyId,
                StockBatchMovementType.SALE);
    }

    public ConsumptionResult consumeFifo(
            Long itemId,
            Long warehouseId,
            int quantity,
            String referenceType,
            Long referenceId,
            Long companyId,
            StockBatchMovementType movementType
    ) {
        if (quantity <= 0) {
            return new ConsumptionResult(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Item item = loadItem(itemId, companyId);
        loadWarehouse(warehouseId, companyId);

        List<StockBatch> batches = batchRepo.findAvailableFifo(companyId, itemId, warehouseId);
        int remaining = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<BatchConsumptionLine> lines = new ArrayList<>();

        for (StockBatch batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int onHand = nz(batch.getQuantityOnHand());
            if (onHand <= 0) {
                continue;
            }
            int take = Math.min(remaining, onHand);
            batch.setQuantityOnHand(onHand - take);
            batchRepo.save(batch);
            recordMovement(
                    batch,
                    movementType,
                    -take,
                    batch.getUnitCost(),
                    referenceType,
                    referenceId
            );
            lines.add(new BatchConsumptionLine(
                    batch.getId(),
                    batch.getBatchNo(),
                    take,
                    batch.getUnitCost()
            ));
            totalCost = totalCost.add(batch.getUnitCost().multiply(BigDecimal.valueOf(take)));
            remaining -= take;
        }

        if (remaining > 0) {
            if (!item.isNegativeStockPermitted()) {
                throw new RuntimeException(
                        "Insufficient batch stock for item " + itemId + " at warehouse " + warehouseId
                                + ". Short by " + remaining + " units.");
            }
        }

        stockService.decreaseForConfirmedSale(itemId, warehouseId, quantity, companyId);
        recomputeItemWeightedAverage(itemId);

        BigDecimal weighted = totalCost.divide(
                BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
        return new ConsumptionResult(lines, totalCost.setScale(2, RoundingMode.HALF_UP), weighted);
    }

    /**
     * Ensures FIFO batches cover IWS on-hand (seeds an ADJUSTMENT batch when IWS is ahead of
     * batches). Then asserts both IWS available and batch on-hand can fulfill {@code quantity}.
     */
    public void assertAvailableForSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        Item item = loadItem(itemId, companyId);
        if (item.isNegativeStockPermitted()) {
            return;
        }
        stockService.assertAvailableForSale(itemId, warehouseId, quantity, companyId);
        if (quantity <= 0) {
            return;
        }
        syncBatchesToMatchIws(itemId, warehouseId, companyId);
        int batchQty = batchRepo.sumQuantityOnHand(companyId, itemId, warehouseId);
        if (batchQty < quantity) {
            throw new RuntimeException(
                    "Insufficient batch stock for item "
                            + itemId
                            + " at warehouse "
                            + warehouseId
                            + ". Available: "
                            + batchQty
                            + ", requested: "
                            + quantity);
        }
    }

    /**
     * If warehouse on-hand exceeds sum of batch layers, seed an ADJUSTMENT batch for the gap
     * (does not change IWS — it already holds the quantity).
     */
    public void syncBatchesToMatchIws(Long itemId, Long warehouseId, Long companyId) {
        Item item = loadItem(itemId, companyId);
        Warehouse warehouse = loadWarehouse(warehouseId, companyId);
        int iwsQty = stockService.getQuantityOnHand(itemId, warehouseId, companyId);
        int batchQty = batchRepo.sumQuantityOnHand(companyId, itemId, warehouseId);
        int gap = iwsQty - batchQty;
        if (gap <= 0) {
            return;
        }
        BigDecimal cost = item.getCostPrice() != null ? item.getCostPrice() : BigDecimal.ZERO;
        String batchNo = "ADJ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        StockBatch batch = batchRepo
                .findByCompanyIdAndItemIdAndWarehouseIdAndBatchNoAndUnitCost(
                        companyId, itemId, warehouseId, batchNo, cost)
                .orElseGet(() -> StockBatch.builder()
                        .company(Company.builder().id(companyId).build())
                        .item(item)
                        .warehouse(warehouse)
                        .batchNo(batchNo)
                        .quantityOnHand(0)
                        .unitCost(cost)
                        .receivedAt(LocalDate.now())
                        .sourceType(StockBatchSourceType.ADJUSTMENT)
                        .sourceId(null)
                        .createdAt(Instant.now())
                        .build());
        batch.setQuantityOnHand(nz(batch.getQuantityOnHand()) + gap);
        StockBatch saved = batchRepo.save(batch);
        recordMovement(saved, StockBatchMovementType.RECEIVE, gap, cost, "ADJUSTMENT_SYNC", null);
    }

    public void restoreByReference(String referenceType, Long referenceId, Long companyId) {
        List<StockBatchMovement> outs = movementRepo
                .findByReferenceTypeAndReferenceIdAndMovementTypeOrderByCreatedAtDesc(
                        referenceType, referenceId, StockBatchMovementType.SALE);
        if (outs.isEmpty()) {
            return;
        }

        int totalRestore = 0;
        Long itemId = null;
        Long warehouseId = null;

        for (StockBatchMovement out : outs) {
            StockBatch batch = out.getStockBatch();
            if (!batch.getCompany().getId().equals(companyId)) {
                throw new RuntimeException("Batch access denied");
            }
            int qty = Math.abs(out.getQuantity());
            batch.setQuantityOnHand(nz(batch.getQuantityOnHand()) + qty);
            batchRepo.save(batch);
            recordMovement(
                    batch,
                    StockBatchMovementType.RESTORE,
                    qty,
                    out.getUnitCost(),
                    referenceType,
                    referenceId
            );
            itemId = batch.getItem().getId();
            warehouseId = batch.getWarehouse().getId();
            totalRestore += qty;
        }

        if (totalRestore > 0 && itemId != null && warehouseId != null) {
            stockService.restoreForCancelledSale(itemId, warehouseId, totalRestore, companyId);
            recomputeItemWeightedAverage(itemId);
        }
    }

    public ConsumptionResult consumeFifoForVariance(
            Long itemId,
            Long warehouseId,
            int quantity,
            Long varianceId,
            Long companyId
    ) {
        return consumeFifo(
                itemId,
                warehouseId,
                quantity,
                REF_STOCK_VARIANCE,
                varianceId,
                companyId,
                StockBatchMovementType.ADJUSTMENT);
    }

    public void transferFifo(
            Long itemId,
            Long fromWarehouseId,
            Long toWarehouseId,
            int quantity,
            Long transferReferenceId,
            Long companyId
    ) {
        ConsumptionResult consumed = consumeFifo(
                itemId,
                fromWarehouseId,
                quantity,
                REF_WAREHOUSE_TRANSFER,
                transferReferenceId,
                companyId
        );

        for (BatchConsumptionLine line : consumed.lines()) {
            receiveIntoBatch(
                    itemId,
                    toWarehouseId,
                    line.quantity(),
                    line.unitCost(),
                    "XFER-" + line.batchId(),
                    null,
                    StockBatchSourceType.TRANSFER,
                    transferReferenceId,
                    companyId
            );
        }
    }

    public void applySignedDelta(
            Long itemId,
            Long warehouseId,
            int delta,
            BigDecimal unitCost,
            String referenceType,
            Long referenceId,
            Long companyId
    ) {
        if (delta == 0) {
            return;
        }
        if (delta > 0) {
            Item item = loadItem(itemId, companyId);
            BigDecimal cost = unitCost != null ? unitCost : normalizeCost(item.getCostPrice());
            receiveIntoBatch(
                    itemId,
                    warehouseId,
                    delta,
                    cost,
                    null,
                    null,
                    StockBatchSourceType.ADJUSTMENT,
                    referenceId,
                    companyId
            );
            return;
        }
        consumeFifo(itemId, warehouseId, Math.abs(delta), referenceType, referenceId, companyId);
    }

    @Transactional(readOnly = true)
    public List<StockBatchResponseDTO> listBatchesForItem(Long itemId, Long warehouseId, Long companyId) {
        loadItem(itemId, companyId);
        return batchRepo.findByItemForCompany(companyId, itemId, warehouseId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockBatchReportDTO buildBatchReport(
            Long companyId,
            Long warehouseId,
            Long itemId,
            String batchNo
    ) {
        List<StockBatchResponseDTO> rows = batchRepo
                .findForReport(companyId, warehouseId, itemId, batchNo)
                .stream()
                .map(this::toDto)
                .toList();

        BigDecimal totalValue = BigDecimal.ZERO;
        long totalQty = 0;
        for (StockBatchResponseDTO row : rows) {
            totalValue = totalValue.add(
                    row.getLineValue() == null ? BigDecimal.ZERO : row.getLineValue());
            totalQty += row.getQuantityOnHand() == null ? 0 : row.getQuantityOnHand();
        }

        return StockBatchReportDTO.builder()
                .batches(rows)
                .totalValueAtCost(totalValue.setScale(2, RoundingMode.HALF_UP))
                .totalQuantity(totalQty)
                .build();
    }

    @Transactional(readOnly = true)
    public BigDecimal sumBatchValueForReport(Long companyId, Long warehouseId, String category) {
        BigDecimal value = batchRepo.sumBatchValueAtCost(companyId, warehouseId, category);
        return value == null ? BigDecimal.ZERO : value;
    }

    @Transactional(readOnly = true)
    public StockBatchMovementReportDTO buildMovementReport(
            Long companyId,
            Long warehouseId,
            Long itemId,
            int limit
    ) {
        return buildMovementReport(companyId, warehouseId, itemId, 0, limit, false);
    }

    @Transactional(readOnly = true)
    public StockBatchMovementReportDTO buildMovementReport(
            Long companyId,
            Long warehouseId,
            Long itemId,
            int page,
            int size,
            boolean archived
    ) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        List<StockBatchMovement> raw = movementRepo.findHistory(
                companyId, itemId, warehouseId, archived, PageRequest.of(pageIndex, pageSize));
        long total = movementRepo.countHistory(companyId, itemId, warehouseId, archived);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);

        List<StockBatchMovementResponseDTO> movements = raw.stream()
                .map(this::toMovementDto)
                .toList();

        // Trend chart uses a wider recent window, independent of the table page.
        List<StockBatchMovementResponseDTO> trendSource = movementRepo.findHistory(
                        companyId, itemId, warehouseId, archived, PageRequest.of(0, 500))
                .stream()
                .map(this::toMovementDto)
                .toList();

        return StockBatchMovementReportDTO.builder()
                .movements(movements)
                .receiveTrend(buildReceiveTrend(trendSource))
                .totalMovements(total)
                .page(pageIndex)
                .size(pageSize)
                .totalPages(totalPages)
                .archived(archived)
                .build();
    }

    public StockBatchMovementResponseDTO archiveMovement(Long companyId, Long id) {
        StockBatchMovement movement = movementRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Movement not found"));
        if (!movement.isArchived()) {
            movement.setArchived(true);
            movementRepo.save(movement);
        }
        return toMovementDto(movement);
    }

    public int archiveMovements(Long companyId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int archived = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Optional<StockBatchMovement> found = movementRepo.findByIdAndCompanyId(id, companyId);
            if (found.isEmpty()) {
                continue;
            }
            StockBatchMovement movement = found.get();
            if (!movement.isArchived()) {
                movement.setArchived(true);
                movementRepo.save(movement);
                archived++;
            }
        }
        return archived;
    }

    @Transactional(readOnly = true)
    public StockBatchInsightsDTO buildInsights(
            Long companyId,
            Long warehouseId,
            Long itemId
    ) {
        List<StockBatch> batches = batchRepo.findForReport(companyId, warehouseId, itemId, null);
        LocalDate today = LocalDate.now();
        LocalDate in30 = today.plusDays(30);
        LocalDate in60 = today.plusDays(60);
        LocalDate in90 = today.plusDays(90);

        int qty30 = 0, qty60 = 0, qty90 = 0;
        BigDecimal val30 = BigDecimal.ZERO, val60 = BigDecimal.ZERO, val90 = BigDecimal.ZERO;
        Map<BigDecimal, int[]> layers = new LinkedHashMap<>();
        List<StockBatchResponseDTO> expiringSoon = new ArrayList<>();

        for (StockBatch batch : batches) {
            int qty = nz(batch.getQuantityOnHand());
            if (qty <= 0) {
                continue;
            }
            BigDecimal unitCost = batch.getUnitCost() == null ? BigDecimal.ZERO : batch.getUnitCost();
            BigDecimal lineVal = unitCost.multiply(BigDecimal.valueOf(qty));
            layers.computeIfAbsent(unitCost, k -> new int[]{0})[0] += qty;

            LocalDate expiry = batch.getExpiryDate();
            if (expiry != null && !expiry.isBefore(today)) {
                if (!expiry.isAfter(in30)) {
                    qty30 += qty;
                    val30 = val30.add(lineVal);
                }
                if (!expiry.isAfter(in60)) {
                    qty60 += qty;
                    val60 = val60.add(lineVal);
                }
                if (!expiry.isAfter(in90)) {
                    qty90 += qty;
                    val90 = val90.add(lineVal);
                    expiringSoon.add(toDto(batch));
                }
            }
        }

        List<StockBatchCostLayerDTO> costLayers = layers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int q = e.getValue()[0];
                    BigDecimal cost = e.getKey();
                    return StockBatchCostLayerDTO.builder()
                            .label(cost.toPlainString())
                            .unitCost(cost)
                            .quantity(q)
                            .value(cost.multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP))
                            .build();
                })
                .toList();

        expiringSoon.sort(Comparator.comparing(
                b -> b.getExpiryDate() == null ? LocalDate.MAX.toString() : b.getExpiryDate().toString()));

        return StockBatchInsightsDTO.builder()
                .expiringWithin30Days(qty30)
                .expiringWithin60Days(qty60)
                .expiringWithin90Days(qty90)
                .valueExpiringWithin30Days(val30.setScale(2, RoundingMode.HALF_UP))
                .valueExpiringWithin60Days(val60.setScale(2, RoundingMode.HALF_UP))
                .valueExpiringWithin90Days(val90.setScale(2, RoundingMode.HALF_UP))
                .valueByCostLayer(costLayers)
                .expiringSoon(expiringSoon.stream().limit(25).toList())
                .build();
    }

    private List<StockBatchHistoryPointDTO> buildReceiveTrend(
            List<StockBatchMovementResponseDTO> movements
    ) {
        Map<String, StockBatchHistoryPointDTO> byMonth = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        for (StockBatchMovementResponseDTO m : movements) {
            if (m.getCreatedAt() == null) {
                continue;
            }
            String period = m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).format(fmt);
            StockBatchHistoryPointDTO point = byMonth.computeIfAbsent(period, p ->
                    StockBatchHistoryPointDTO.builder()
                            .period(p)
                            .receiveQty(0)
                            .issueQty(0)
                            .receiveValue(BigDecimal.ZERO)
                            .issueValue(BigDecimal.ZERO)
                            .build());

            int absQty = Math.abs(m.getQuantity() == null ? 0 : m.getQuantity());
            BigDecimal lineVal = m.getLineValue() == null ? BigDecimal.ZERO : m.getLineValue().abs();

            if (m.getQuantity() != null && m.getQuantity() > 0) {
                point.setReceiveQty(point.getReceiveQty() + absQty);
                point.setReceiveValue(point.getReceiveValue().add(lineVal));
            } else {
                point.setIssueQty(point.getIssueQty() + absQty);
                point.setIssueValue(point.getIssueValue().add(lineVal));
            }
        }

        return byMonth.values().stream()
                .sorted(Comparator.comparing(StockBatchHistoryPointDTO::getPeriod))
                .toList();
    }

    private StockBatchMovementResponseDTO toMovementDto(StockBatchMovement m) {
        StockBatch batch = m.getStockBatch();
        int qty = m.getQuantity() == null ? 0 : m.getQuantity();
        BigDecimal unitCost = m.getUnitCost() == null ? BigDecimal.ZERO : m.getUnitCost();
        return StockBatchMovementResponseDTO.builder()
                .id(m.getId())
                .batchId(batch.getId())
                .batchNo(batch.getBatchNo())
                .itemId(batch.getItem().getId())
                .itemSku(batch.getItem().getSku())
                .itemName(batch.getItem().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .movementType(m.getMovementType())
                .quantity(qty)
                .unitCost(unitCost)
                .lineValue(unitCost.multiply(BigDecimal.valueOf(Math.abs(qty))).setScale(2, RoundingMode.HALF_UP))
                .referenceType(m.getReferenceType())
                .referenceId(m.getReferenceId())
                .createdAt(m.getCreatedAt())
                .archived(m.isArchived())
                .build();
    }

    public void recomputeItemWeightedAverage(Long itemId) {
        Item item = itemRepo.findById(itemId).orElseThrow(() -> new RuntimeException("Item not found"));
        List<StockBatch> batches = batchRepo.findByItemForCompany(
                item.getCompany().getId(), itemId, null);

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (StockBatch batch : batches) {
            int qty = nz(batch.getQuantityOnHand());
            if (qty <= 0) {
                continue;
            }
            totalQty = totalQty.add(BigDecimal.valueOf(qty));
            totalValue = totalValue.add(
                    batch.getUnitCost().multiply(BigDecimal.valueOf(qty)));
        }

        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
            item.setCostPrice(totalValue.divide(totalQty, 2, RoundingMode.HALF_UP));
            itemRepo.save(item);
        }
    }

    private void recordMovement(
            StockBatch batch,
            StockBatchMovementType type,
            int signedQty,
            BigDecimal unitCost,
            String referenceType,
            Long referenceId
    ) {
        movementRepo.save(StockBatchMovement.builder()
                .stockBatch(batch)
                .movementType(type)
                .quantity(signedQty)
                .unitCost(unitCost)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .createdAt(Instant.now())
                .archived(false)
                .build());
    }

    private StockBatchResponseDTO toDto(StockBatch batch) {
        int qty = nz(batch.getQuantityOnHand());
        BigDecimal unitCost = batch.getUnitCost() == null ? BigDecimal.ZERO : batch.getUnitCost();
        return StockBatchResponseDTO.builder()
                .id(batch.getId())
                .itemId(batch.getItem().getId())
                .itemSku(batch.getItem().getSku())
                .itemName(batch.getItem().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .batchNo(batch.getBatchNo())
                .quantityOnHand(qty)
                .unitCost(unitCost)
                .lineValue(unitCost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP))
                .receivedAt(batch.getReceivedAt())
                .expiryDate(batch.getExpiryDate())
                .sourceType(batch.getSourceType())
                .sourceId(batch.getSourceId())
                .build();
    }

    private String resolveBatchNo(String batchNo) {
        if (batchNo != null && !batchNo.isBlank()) {
            return batchNo.trim();
        }
        return "RCV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + System.currentTimeMillis() % 100000;
    }

    private BigDecimal normalizeCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return unitCost.setScale(2, RoundingMode.HALF_UP);
    }

    private Item loadItem(Long itemId, Long companyId) {
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        if (!item.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Item not found");
        }
        return item;
    }

    private Warehouse loadWarehouse(Long warehouseId, Long companyId) {
        Warehouse wh = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        if (!wh.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Warehouse not found");
        }
        return wh;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
