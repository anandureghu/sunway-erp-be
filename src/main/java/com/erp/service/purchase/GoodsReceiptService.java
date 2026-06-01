package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.purchase.GoodsReceipt;
import com.erp.domain.purchase.GoodsReceiptItem;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.dto.purchase.GoodsReceiptCreateDTO;
import com.erp.dto.purchase.GoodsReceiptItemDTO;
import com.erp.dto.purchase.GoodsReceiptResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.inventory.ItemWarehouseStockService;
import com.erp.service.pdf.GoodsReceiptPdfService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class GoodsReceiptService {

    private final GoodsReceiptRepository repo;
    private final PurchaseOrderRepository poRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final GoodsReceiptPdfService goodsReceiptPdfService;

    public GoodsReceiptService(
            GoodsReceiptRepository repo,
            PurchaseOrderRepository poRepo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            ItemWarehouseStockService itemWarehouseStockService,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            GoodsReceiptPdfService goodsReceiptPdfService
    ) {
        this.repo = repo;
        this.poRepo = poRepo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.goodsReceiptPdfService = goodsReceiptPdfService;
    }

    public GoodsReceiptResponseDTO receive(GoodsReceiptCreateDTO dto) {

        PurchaseOrder po = poRepo.findById(dto.getPurchaseOrderId())
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new RuntimeException("Purchase order not ready for receiving");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        Long companyId = auth.getCurrentCompanyId();

        List<GoodsReceiptItem> items = dto.getItems().stream().map(i -> {

            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            if (i.getWarehouseId() == null) {
                throw new RuntimeException("warehouseId is required for each receipt line");
            }
            Warehouse wh = warehouseRepo.findById(i.getWarehouseId())
                    .filter(w -> w.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new RuntimeException("Warehouse not found"));

            int accepted = i.getAcceptedQty() == null ? 0 : i.getAcceptedQty();
            if (accepted > 0) {
                itemWarehouseStockService.addIncomingStock(
                        item.getId(), wh.getId(), accepted, companyId);
                item.setDateReceived(LocalDate.now());
                itemRepo.save(item);
            }

            return GoodsReceiptItem.builder()
                    .item(item)
                    .warehouse(wh)
                    .receivedQty(i.getReceivedQty())
                    .acceptedQty(i.getAcceptedQty())
                    .rejectedQty(i.getRejectedQty())
                    .remarks(i.getRemarks())
                    .build();
        }).collect(Collectors.toCollection(ArrayList::new));

        GoodsReceipt grn = GoodsReceipt.builder()
                .purchaseOrder(po)
                .company(company)
                .receivedBy(user)
                .items(items)
                .build();

        // Update PO status
        po.setStatus(PurchaseOrderStatus.RECEIVED);

        GoodsReceipt saved = repo.save(grn);
        GoodsReceipt forPdf = repo.findById(saved.getId()).orElse(saved);
        String pdfUrl = goodsReceiptPdfService.generateAndUploadGoodsReceiptPdf(forPdf);
        forPdf.setDocumentPdfUrl(pdfUrl);
        return toDTO(repo.save(forPdf));
    }

    public GoodsReceiptResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public String getOrCreateReceiptPdfUrl(Long id) {
        GoodsReceipt gr = getEntity(id);
        if (gr.getDocumentPdfUrl() != null && !gr.getDocumentPdfUrl().isBlank()) {
            return gr.getDocumentPdfUrl();
        }
        GoodsReceipt loaded = repo.findById(gr.getId()).orElse(gr);
        String url = goodsReceiptPdfService.generateAndUploadGoodsReceiptPdf(loaded);
        loaded.setDocumentPdfUrl(url);
        repo.save(loaded);
        return url;
    }

    private GoodsReceipt getEntity(Long id) {
        return repo.findById(id)
                .filter(r -> r.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Goods receipt not found"));
    }

    public List<GoodsReceiptResponseDTO> listByPO(Long purchaseOrderId) {
        poRepo.findById(purchaseOrderId)
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
        return repo.findByPurchaseOrderId(purchaseOrderId)
                .stream().map(this::toDTO).toList();
    }

    private GoodsReceiptResponseDTO toDTO(GoodsReceipt gr) {
        return GoodsReceiptResponseDTO.builder()
                .id(gr.getId())
                .purchaseOrderId(gr.getPurchaseOrder().getId())
                .receivedAt(gr.getReceivedAt())
                .documentPdfUrl(gr.getDocumentPdfUrl())
                .items(
                        gr.getItems().stream().map(i ->
                                GoodsReceiptItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .warehouseId(
                                                i.getWarehouse() != null
                                                        ? i.getWarehouse().getId()
                                                        : i.getItem().getWarehouse().getId())
                                        .receivedQty(i.getReceivedQty())
                                        .acceptedQty(i.getAcceptedQty())
                                        .rejectedQty(i.getRejectedQty())
                                        .remarks(i.getRemarks())
                                        .build()
                        ).toList()
                )
                .build();
    }
}
