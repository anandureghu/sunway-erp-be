package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
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
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GoodsReceiptService {

    private final GoodsReceiptRepository repo;
    private final PurchaseOrderRepository poRepo;
    private final ItemRepository itemRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public GoodsReceiptService(
            GoodsReceiptRepository repo,
            PurchaseOrderRepository poRepo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.poRepo = poRepo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
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

        List<GoodsReceiptItem> items = dto.getItems().stream().map(i -> {

            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // 🔑 STOCK UPDATE (accepted only)
            item.setQuantity(item.getQuantity() + i.getAcceptedQty());
            item.setAvailable(item.getAvailable() + i.getAcceptedQty());

            return GoodsReceiptItem.builder()
                    .item(item)
                    .receivedQty(i.getReceivedQty())
                    .acceptedQty(i.getAcceptedQty())
                    .rejectedQty(i.getRejectedQty())
                    .remarks(i.getRemarks())
                    .build();
        }).toList();

        GoodsReceipt grn = GoodsReceipt.builder()
                .purchaseOrder(po)
                .company(company)
                .receivedBy(user)
                .items(items)
                .build();

        // Update PO status
        po.setStatus(PurchaseOrderStatus.RECEIVED);

        return toDTO(repo.save(grn));
    }

    public List<GoodsReceiptResponseDTO> listByPO(Long purchaseOrderId) {
        return repo.findByPurchaseOrderId(purchaseOrderId)
                .stream().map(this::toDTO).toList();
    }

    private GoodsReceiptResponseDTO toDTO(GoodsReceipt gr) {
        return GoodsReceiptResponseDTO.builder()
                .id(gr.getId())
                .purchaseOrderId(gr.getPurchaseOrder().getId())
                .receivedAt(gr.getReceivedAt())
                .items(
                        gr.getItems().stream().map(i ->
                                GoodsReceiptItemDTO.builder()
                                        .itemId(i.getItem().getId())
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
