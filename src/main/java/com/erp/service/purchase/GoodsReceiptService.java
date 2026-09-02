package com.erp.service.purchase;

import com.erp.domain.InvoiceType;
import com.erp.domain.User;
import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.purchase.GoodsReceipt;
import com.erp.domain.purchase.GoodsReceiptItem;
import com.erp.domain.purchase.GoodsReceiptStatus;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderItem;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.inventory.StockBatchSourceType;
import com.erp.dto.purchase.GoodsReceiptCreateDTO;
import com.erp.dto.purchase.GoodsReceiptItemDTO;
import com.erp.dto.purchase.GoodsReceiptResponseDTO;
import com.erp.dto.purchase.InspectionConfirmDTO;
import com.erp.dto.purchase.StockPostingDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.CreditNoteService;
import com.erp.service.finance.InvoiceService;
import com.erp.service.inventory.ItemWarehouseStockService;
import com.erp.service.inventory.StockBatchService;
import com.erp.service.pdf.GoodsReceiptPdfService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class GoodsReceiptService {

    private final GoodsReceiptRepository repo;
    private final PurchaseOrderRepository poRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final InvoiceRepository invoiceRepo;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final StockBatchService stockBatchService;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final GoodsReceiptPdfService goodsReceiptPdfService;
    private final InvoiceService invoiceService;
    private final CreditNoteService creditNoteService;

    public GoodsReceiptService(
            GoodsReceiptRepository repo,
            PurchaseOrderRepository poRepo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            InvoiceRepository invoiceRepo,
            ItemWarehouseStockService itemWarehouseStockService,
            StockBatchService stockBatchService,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            GoodsReceiptPdfService goodsReceiptPdfService,
            InvoiceService invoiceService,
            CreditNoteService creditNoteService
    ) {
        this.repo = repo;
        this.poRepo = poRepo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.invoiceRepo = invoiceRepo;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.stockBatchService = stockBatchService;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.goodsReceiptPdfService = goodsReceiptPdfService;
        this.invoiceService = invoiceService;
        this.creditNoteService = creditNoteService;
    }

    /**
     * Intake step: logs what physically arrived against a PO line, pending QA.
     * No stock is posted and no PO/invoice effects happen here - that's deferred
     * to {@link #confirmInspection} and {@link #postItemsToStock} respectively.
     */
    public GoodsReceiptResponseDTO receive(GoodsReceiptCreateDTO dto) {

        PurchaseOrder po = poRepo.findById(dto.getPurchaseOrderId())
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new NotFoundException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException("Purchase order not ready for receiving");
        }

        boolean hasOpenIntake = repo.findByPurchaseOrderId(po.getId()).stream()
                .anyMatch(existing -> !existing.isArchived()
                        && existing.getStatus() == GoodsReceiptStatus.PENDING_INSPECTION);
        if (hasOpenIntake) {
            throw new ConflictException(
                    "This purchase order already has a goods receipt awaiting inspection.");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        List<GoodsReceiptItem> items = dto.getItems().stream().map(i -> {

            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new NotFoundException("Item not found"));

            PurchaseOrderItem poItem = resolvePurchaseOrderItem(po, i.getPurchaseOrderItemId(), i.getItemId());

            int remaining = poItem.getRemainingQuantity();
            Integer receivedQty = i.getReceivedQty();
            if (receivedQty == null || receivedQty < 0) {
                throw new ConflictException("receivedQty must be zero or greater for item " + item.getId());
            }
            if (receivedQty > remaining) {
                throw new ConflictException(
                        "receivedQty (" + receivedQty + ") exceeds remaining orderable quantity (" + remaining
                                + ") for item " + item.getId());
            }

            return GoodsReceiptItem.builder()
                    .item(item)
                    .purchaseOrderItem(poItem)
                    .orderedQuantity(remaining)
                    .receivedQty(receivedQty)
                    .remarks(i.getRemarks())
                    .build();
        }).collect(Collectors.toCollection(ArrayList::new));

        GoodsReceipt grn = GoodsReceipt.builder()
                .purchaseOrder(po)
                .company(company)
                .receivedBy(user)
                .status(GoodsReceiptStatus.PENDING_INSPECTION)
                .items(items)
                .build();

        GoodsReceipt saved = repo.save(grn);
        GoodsReceipt forPdf = repo.findById(saved.getId()).orElse(saved);
        String pdfUrl = goodsReceiptPdfService.generateAndUploadGoodsReceiptPdf(forPdf);
        forPdf.setDocumentPdfUrl(pdfUrl);
        return toDTO(repo.save(forPdf));
    }

    /**
     * QA sign-off: confirms accepted/rejected quantities per line (the inspector may amend
     * what was logged at receiving), updates the PO line's cumulative received/rejected
     * quantity and overall PO status, and - if any quantity was rejected - either reduces
     * the still-unpaid purchase invoice or raises an automatic supplier credit note when
     * the invoice was already settled.
     */
    public GoodsReceiptResponseDTO confirmInspection(Long id, InspectionConfirmDTO dto) {
        GoodsReceipt gr = getEntity(id);
        if (gr.getStatus() != GoodsReceiptStatus.PENDING_INSPECTION) {
            throw new ConflictException("Goods receipt has already been inspected");
        }

        Map<Long, InspectionConfirmDTO.Line> linesById = new HashMap<>();
        if (dto != null && dto.getItems() != null) {
            for (InspectionConfirmDTO.Line line : dto.getItems()) {
                linesById.put(line.getGoodsReceiptItemId(), line);
            }
        }

        PurchaseOrder po = gr.getPurchaseOrder();
        BigDecimal totalRejectedValue = BigDecimal.ZERO;

        for (GoodsReceiptItem item : gr.getItems()) {
            InspectionConfirmDTO.Line line = linesById.get(item.getId());
            int received = item.getReceivedQty() == null ? 0 : item.getReceivedQty();
            int accepted = line != null && line.getAcceptedQty() != null ? line.getAcceptedQty() : received;
            int rejected = line != null && line.getRejectedQty() != null ? line.getRejectedQty() : 0;
            int orderedQuantity = item.getOrderedQuantity() == null ? 0 : item.getOrderedQuantity();

            if (accepted < 0 || rejected < 0) {
                throw new ConflictException("acceptedQty/rejectedQty must be zero or greater");
            }
            if (received == 0) {
                // Nothing physically arrived for this line. Accepted must stay zero,
                // but the inspector may write off up to the full remaining ordered
                // quantity as rejected (e.g. supplier short-shipment/cancellation)
                // instead of leaving it open indefinitely for a future receipt.
                if (accepted != 0) {
                    throw new ConflictException(
                            "acceptedQty must be zero when nothing was received for item " + item.getItem().getId());
                }
                if (rejected > orderedQuantity) {
                    throw new ConflictException(
                            "rejectedQty (" + rejected + ") cannot exceed ordered quantity (" + orderedQuantity
                                    + ") for item " + item.getItem().getId());
                }
            } else if (accepted + rejected != received) {
                throw new ConflictException(
                        "accepted (" + accepted + ") + rejected (" + rejected + ") must equal received (" + received
                                + ") for item " + item.getItem().getId());
            }

            item.setAcceptedQty(accepted);
            item.setRejectedQty(rejected);
            if (line != null && line.getRemarks() != null) {
                item.setRemarks(line.getRemarks());
            }

            PurchaseOrderItem poItem = item.getPurchaseOrderItem();
            if (poItem != null) {
                poItem.setReceivedQty((poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty()) + accepted);
                poItem.setRejectedQty((poItem.getRejectedQty() == null ? 0 : poItem.getRejectedQty()) + rejected);

                if (rejected > 0 && poItem.getUnitCost() != null) {
                    BigDecimal rejectedValue = poItem.getUnitCost().multiply(BigDecimal.valueOf(rejected));
                    totalRejectedValue = totalRejectedValue.add(rejectedValue);
                    // Reduce line cost so PO totals reflect what was actually kept
                    BigDecimal currentLine = poItem.getLineTotal() != null ? poItem.getLineTotal() : BigDecimal.ZERO;
                    poItem.setLineTotal(currentLine.subtract(rejectedValue).max(BigDecimal.ZERO));
                }
            }
        }

        recomputePurchaseOrderStatus(po);
        recomputePurchaseOrderTotal(po);
        poRepo.save(po);

        InspectionFinanceOutcome finance = InspectionFinanceOutcome.none();
        if (totalRejectedValue.compareTo(BigDecimal.ZERO) > 0) {
            finance = applyRejectionToInvoice(po, totalRejectedValue, gr.getId());
        }

        Instant now = Instant.now();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        gr.setStatus(GoodsReceiptStatus.INSPECTED);
        gr.setInspectedBy(user);
        gr.setInspectedAt(now);
        gr.setAuthorizedBy(user);

        GoodsReceipt saved = repo.save(gr);
        String pdfUrl = goodsReceiptPdfService.generateAndUploadGoodsReceiptPdf(saved);
        saved.setDocumentPdfUrl(pdfUrl);
        return toDTO(repo.save(saved), finance);
    }

    private InspectionFinanceOutcome applyRejectionToInvoice(
            PurchaseOrder po, BigDecimal rejectedValue, Long goodsReceiptId) {
        Invoice invoice = invoiceRepo.findByOrderIdAndType(po.getId(), InvoiceType.PURCHASE).orElse(null);
        String reason = "Goods rejected at inspection for purchase order " + po.getOrderNumber();

        if (invoice == null) {
            return InspectionFinanceOutcome.none();
        }

        InvoiceService.GoodsAdjustmentResult result =
                invoiceService.reduceForRejectedGoods(po.getId(), rejectedValue, reason);

        BigDecimal creditAmount = result.creditAmount() != null ? result.creditAmount() : BigDecimal.ZERO;
        String creditNoteNumber = null;
        if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
            var cn = creditNoteService.createAutomaticCreditNoteForRejection(
                    invoice.getCompany().getId(),
                    invoice.getInvoiceId(),
                    creditAmount,
                    reason,
                    goodsReceiptId);
            creditNoteNumber = cn != null ? cn.getCreditNoteNumber() : null;
        }
        return new InspectionFinanceOutcome(
                result.reducedAmount() != null ? result.reducedAmount() : BigDecimal.ZERO,
                creditAmount,
                creditNoteNumber);
    }

    private record InspectionFinanceOutcome(
            BigDecimal invoiceReducedAmount,
            BigDecimal creditNoteAmount,
            String creditNoteNumber) {
        static InspectionFinanceOutcome none() {
            return new InspectionFinanceOutcome(BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }

    private void recomputePurchaseOrderStatus(PurchaseOrder po) {
        boolean allResolved = true;
        boolean anyResolved = false;
        for (PurchaseOrderItem poItem : po.getItems()) {
            int received = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            int rejected = poItem.getRejectedQty() == null ? 0 : poItem.getRejectedQty();
            if (received + rejected > 0) {
                anyResolved = true;
            }
            if (poItem.getRemainingQuantity() > 0) {
                allResolved = false;
            }
        }
        if (allResolved) {
            po.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (anyResolved) {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
    }

    /** Rebuild PO total from line totals after rejection cost adjustments. */
    private void recomputePurchaseOrderTotal(PurchaseOrder po) {
        if (po.getItems() == null || po.getItems().isEmpty()) {
            po.setTotalAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem line : po.getItems()) {
            if (line.getLineTotal() != null) {
                total = total.add(line.getLineTotal());
            }
        }
        po.setTotalAmount(total);
    }

    /**
     * Posts an inspected line's accepted quantity into a warehouse stock batch - the
     * logistics step (warehouse/batch/lot/unit-cost) deferred out of inspection. One
     * posting per line; a line already posted is skipped rather than double-stocked.
     */
    public GoodsReceiptResponseDTO postItemsToStock(Long id, StockPostingDTO dto) {
        GoodsReceipt gr = getEntity(id);
        if (gr.getStatus() != GoodsReceiptStatus.INSPECTED) {
            throw new ConflictException("Goods receipt must be inspected before stock can be posted");
        }

        Map<Long, StockPostingDTO.Line> linesById = new HashMap<>();
        if (dto != null && dto.getItems() != null) {
            for (StockPostingDTO.Line line : dto.getItems()) {
                linesById.put(line.getGoodsReceiptItemId(), line);
            }
        }

        Long companyId = auth.getCurrentCompanyId();
        Map<Long, GoodsReceiptItem> itemsById = gr.getItems().stream()
                .collect(Collectors.toMap(GoodsReceiptItem::getId, i -> i));

        for (StockPostingDTO.Line line : linesById.values()) {
            GoodsReceiptItem item = itemsById.get(line.getGoodsReceiptItemId());
            if (item == null) {
                throw new NotFoundException("Goods receipt item not found: " + line.getGoodsReceiptItemId());
            }
            if (item.getStockedAt() != null) {
                continue; // already posted
            }
            int accepted = item.getAcceptedQty() == null ? 0 : item.getAcceptedQty();
            if (accepted <= 0) {
                continue; // nothing to stock
            }
            if (line.getWarehouseId() == null) {
                throw new ConflictException("warehouseId is required to post item " + item.getItem().getId() + " to stock");
            }
            Warehouse wh = warehouseRepo.findById(line.getWarehouseId())
                    .filter(w -> w.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new NotFoundException("Warehouse not found"));

            BigDecimal unitCost = resolveUnitCost(line.getUnitCost(), item);

            stockBatchService.receiveIntoBatch(
                    item.getItem().getId(),
                    wh.getId(),
                    accepted,
                    unitCost,
                    line.getBatchNo(),
                    line.getExpiryDate(),
                    StockBatchSourceType.GOODS_RECEIPT,
                    gr.getId(),
                    companyId
            );

            Item catalogItem = item.getItem();
            catalogItem.setDateReceived(java.time.LocalDate.now());
            if (line.getExpiryDate() != null) {
                catalogItem.setExpiryDate(line.getExpiryDate());
            }
            itemRepo.save(catalogItem);

            item.setWarehouse(wh);
            item.setBatchNo(line.getBatchNo());
            item.setLotNo(line.getLotNo());
            item.setUnitCost(unitCost);
            item.setStockedAt(Instant.now());
        }

        return toDTO(repo.save(gr));
    }

    private BigDecimal resolveUnitCost(BigDecimal requested, GoodsReceiptItem item) {
        // Always prefer the PO line cost so receive cannot override confirmed purchase price.
        PurchaseOrderItem poItem = item.getPurchaseOrderItem();
        if (poItem != null && poItem.getUnitCost() != null) {
            return poItem.getUnitCost();
        }
        if (requested != null) {
            return requested;
        }
        return item.getItem().getCostPrice() != null ? item.getItem().getCostPrice() : BigDecimal.ZERO;
    }

    /** Inspected, non-archived receipts with at least one accepted line not yet posted to stock. */
    public List<GoodsReceiptResponseDTO> listAwaitingStock() {
        return repo.findByCompany_IdAndStatusAndArchivedFalseOrderByReceivedAtDesc(
                        auth.getCurrentCompanyId(), GoodsReceiptStatus.INSPECTED)
                .stream()
                .filter(gr -> gr.getItems().stream().anyMatch(this::awaitingStock))
                .map(this::toDTO)
                .toList();
    }

    private boolean awaitingStock(GoodsReceiptItem item) {
        int accepted = item.getAcceptedQty() == null ? 0 : item.getAcceptedQty();
        return accepted > 0 && item.getStockedAt() == null;
    }

    public GoodsReceiptResponseDTO archive(Long id) {
        GoodsReceipt gr = getEntity(id);
        if (gr.isArchived()) {
            return toDTO(gr);
        }
        if (gr.getStatus() != GoodsReceiptStatus.INSPECTED) {
            throw new ConflictException("Only inspected goods receipts can be archived");
        }
        gr.setArchived(true);
        return toDTO(repo.save(gr));
    }

    private PurchaseOrderItem resolvePurchaseOrderItem(PurchaseOrder po, Long purchaseOrderItemId, Long itemId) {
        if (purchaseOrderItemId != null) {
            return po.getItems().stream()
                    .filter(li -> li.getId().equals(purchaseOrderItemId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Purchase order line " + purchaseOrderItemId + " not found on this purchase order"));
        }
        List<PurchaseOrderItem> matches = po.getItems().stream()
                .filter(li -> li.getItem().getId().equals(itemId))
                .toList();
        if (matches.isEmpty()) {
            throw new NotFoundException("Item " + itemId + " is not on this purchase order");
        }
        if (matches.size() > 1) {
            throw new ConflictException(
                    "Item " + itemId + " appears on more than one line of this purchase order; "
                            + "specify purchaseOrderItemId");
        }
        return matches.get(0);
    }

    public GoodsReceiptResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public String getOrCreateReceiptPdfUrl(Long id) {
        // Always regenerate so template/layout updates are reflected immediately.
        GoodsReceipt loaded = getEntity(id);
        String url = goodsReceiptPdfService.generateAndUploadGoodsReceiptPdf(loaded);
        loaded.setDocumentPdfUrl(url);
        repo.save(loaded);
        return url;
    }

    private GoodsReceipt getEntity(Long id) {
        return repo.findById(id)
                .filter(r -> r.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new NotFoundException("Goods receipt not found"));
    }

    public List<GoodsReceiptResponseDTO> listByPO(Long purchaseOrderId) {
        poRepo.findById(purchaseOrderId)
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new NotFoundException("Purchase order not found"));
        return repo.findByPurchaseOrderId(purchaseOrderId)
                .stream().map(this::toDTO).toList();
    }

    public List<GoodsReceiptResponseDTO> listForCurrentCompany() {
        return repo.findByCompany_IdOrderByReceivedAtDesc(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private GoodsReceiptResponseDTO toDTO(GoodsReceipt gr) {
        return toDTO(gr, null);
    }

    private GoodsReceiptResponseDTO toDTO(GoodsReceipt gr, InspectionFinanceOutcome finance) {
        var po = gr.getPurchaseOrder();
        return GoodsReceiptResponseDTO.builder()
                .id(gr.getId())
                .purchaseOrderId(po.getId())
                .purchaseOrderNumber(po.getOrderNumber())
                .supplierName(po.getSupplier() != null ? po.getSupplier().getVendorName() : null)
                .status(gr.getStatus() != null ? gr.getStatus().name() : null)
                .archived(gr.isArchived())
                .receivedAt(gr.getReceivedAt())
                .receivedById(gr.getReceivedBy() != null ? gr.getReceivedBy().getId() : null)
                .receivedByName(gr.getReceivedBy() != null ? gr.getReceivedBy().getFullName() : null)
                .inspectedById(gr.getInspectedBy() != null ? gr.getInspectedBy().getId() : null)
                .inspectedByName(gr.getInspectedBy() != null ? gr.getInspectedBy().getFullName() : null)
                .inspectedAt(gr.getInspectedAt())
                .authorizedById(gr.getAuthorizedBy() != null ? gr.getAuthorizedBy().getId() : null)
                .authorizedByName(gr.getAuthorizedBy() != null ? gr.getAuthorizedBy().getFullName() : null)
                .documentPdfUrl(gr.getDocumentPdfUrl())
                .invoiceReducedAmount(finance != null ? finance.invoiceReducedAmount() : null)
                .creditNoteAmount(finance != null ? finance.creditNoteAmount() : null)
                .creditNoteNumber(finance != null ? finance.creditNoteNumber() : null)
                .items(
                        gr.getItems().stream().map(i -> {
                            var warehouse = i.getWarehouse() != null
                                    ? i.getWarehouse()
                                    : i.getItem().getWarehouse();
                            return GoodsReceiptItemDTO.builder()
                                    .id(i.getId())
                                    .itemId(i.getItem().getId())
                                    .purchaseOrderItemId(i.getPurchaseOrderItem() != null ? i.getPurchaseOrderItem().getId() : null)
                                    .orderedQuantity(i.getOrderedQuantity())
                                    .warehouseId(warehouse != null ? warehouse.getId() : null)
                                    .warehouseName(warehouse != null ? warehouse.getName() : null)
                                    .receivedQty(i.getReceivedQty())
                                    .acceptedQty(i.getAcceptedQty())
                                    .rejectedQty(i.getRejectedQty())
                                    .remarks(i.getRemarks())
                                    .batchNo(i.getBatchNo())
                                    .lotNo(i.getLotNo())
                                    .unitCost(i.getUnitCost())
                                    .stockedAt(i.getStockedAt())
                                    .build();
                        }).toList()
                )
                .build();
    }
}
