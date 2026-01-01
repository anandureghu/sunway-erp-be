package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.purchase.*;
import com.erp.dto.purchase.*;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class PurchaseRequisitionService {

    private final PurchaseRequisitionRepository repo;
    private final ItemRepository itemRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final VendorRepository vendorRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseOrderService purchaseOrderService;
    private final AuthContext auth;

    public PurchaseRequisitionService(
            PurchaseRequisitionRepository repo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            VendorRepository vendorRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            PurchaseOrderService purchaseOrderService
    ) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.vendorRepo = vendorRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseOrderService = purchaseOrderService;
    }

    public PurchaseRequisitionResponseDTO create(PurchaseRequisitionCreateDTO dto) {

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        List<PurchaseRequisitionItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            return PurchaseRequisitionItem.builder()
                    .item(item)
                    .requestedQty(i.getRequestedQty())
                    .remarks(i.getRemarks())
                    .build();
        }).toList();

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .requisitionNumber("PR-" + System.currentTimeMillis())
                .status(PurchaseRequisitionStatus.DRAFT)
                .company(company)
                .requestedBy(user)
                .items(items)
                .build();

        return toDTO(repo.save(pr));
    }

    public PurchaseRequisitionResponseDTO submit(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT requisitions can be submitted");
        }

        pr.setStatus(PurchaseRequisitionStatus.SUBMITTED);
        return toDTO(repo.save(pr));
    }

    public PurchaseRequisitionResponseDTO approve(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requisitions can be approved");
        }

        User approver = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        pr.setStatus(PurchaseRequisitionStatus.APPROVED);
        pr.setApprovedBy(approver);
        pr.setApprovedAt(java.time.Instant.now());

        return toDTO(repo.save(pr));
    }

    public List<PurchaseRequisitionResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    private PurchaseRequisition getEntity(Long id) {
        return repo.findById(id)
                .filter(pr -> pr.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Requisition not found"));
    }

    private PurchaseRequisitionResponseDTO toDTO(PurchaseRequisition pr) {
        return PurchaseRequisitionResponseDTO.builder()
                .id(pr.getId())
                .requisitionNumber(pr.getRequisitionNumber())
                .status(pr.getStatus().name())
                .createdAt(pr.getCreatedAt())
                .approvedAt(pr.getApprovedAt())
                .items(
                        pr.getItems().stream().map(i ->
                                PurchaseRequisitionItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .requestedQty(i.getRequestedQty())
                                        .remarks(i.getRemarks())
                                        .build()
                        ).toList()
                )
                .build();
    }

    public PurchaseOrderResponseDTO convertToPurchaseOrder(
            Long requisitionId,
            RequisitionToPurchaseOrderDTO dto
    ) {

        PurchaseRequisition pr = repo.findById(requisitionId)
                .filter(r -> r.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Requisition not found"));

        if (pr.getStatus() != PurchaseRequisitionStatus.APPROVED) {
            throw new RuntimeException("Only APPROVED requisitions can be converted");
        }

        Vendor supplier = vendorRepo.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        // 1️⃣ Build PO items (NO mutation)
        List<PurchaseOrderItem> poItems = dto.getItems().stream().map(i -> {

            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            BigDecimal lineTotal = i.getUnitCost()
                    .multiply(BigDecimal.valueOf(i.getQuantity()));

            return PurchaseOrderItem.builder()
                    .item(item)
                    .quantity(i.getQuantity())
                    .unitCost(i.getUnitCost())
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        // 2️⃣ Calculate total safely
        BigDecimal total = poItems.stream()
                .map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3️⃣ Create Purchase Order
        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber("PO-" + System.currentTimeMillis())
                .supplier(supplier)
                .orderDate(dto.getOrderDate())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(total)
                .company(company)
                .createdBy(user)
                .items(poItems)
                .sourceRequisition(pr)
                .build();

        // 4️⃣ Mark PR as converted
        pr.setStatus(PurchaseRequisitionStatus.CONVERTED);
        pr.setConvertedAt(java.time.Instant.now());
        pr.setConvertedBy(user);

        purchaseOrderRepo.save(po);
        repo.save(pr);

        return purchaseOrderService.get(po.getId());
    }
}
