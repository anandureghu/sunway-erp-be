package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderItem;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.dto.purchase.PurchaseOrderCreateDTO;
import com.erp.dto.purchase.PurchaseOrderItemDTO;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.purchase.PurchaseOrderUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.PurchaseInvoiceGenerationScheduler;
import com.erp.service.finance.VendorPayableService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository repo;
    private final VendorRepository vendorRepo;
    private final ItemRepository itemRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final VendorPayableService vendorPayableService;
    private final PurchaseInvoiceGenerationScheduler purchaseInvoiceGenerationScheduler;
    private final AuthContext auth;

    public PurchaseOrderService(
            PurchaseOrderRepository repo,
            VendorRepository vendorRepo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            VendorPayableService vendorPayableService,
            @Lazy PurchaseInvoiceGenerationScheduler purchaseInvoiceGenerationScheduler,
            AuthContext auth
    ) {
        this.repo = repo;
        this.vendorRepo = vendorRepo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.vendorPayableService = vendorPayableService;
        this.purchaseInvoiceGenerationScheduler = purchaseInvoiceGenerationScheduler;
        this.auth = auth;
    }

    public PurchaseOrderResponseDTO create(PurchaseOrderCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Vendor supplier = vendorRepo.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        Company company = companyRepo.findById(companyId).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        BigDecimal total = BigDecimal.ZERO;

        List<PurchaseOrderItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            PurchaseLinePricing.Resolved r = PurchaseLinePricing.resolveOrderLine(
                    item, i.getOtherUnitCost(), i.getUnitCost());
            BigDecimal lineTotal = r.appliedUnitCost()
                    .multiply(BigDecimal.valueOf(i.getQuantity()));

            return PurchaseOrderItem.builder()
                    .item(item)
                    .quantity(i.getQuantity())
                    .actualItemPrice(r.actualItemPrice())
                    .otherUnitCost(r.otherUnitCost())
                    .unitCost(r.appliedUnitCost())
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (PurchaseOrderItem li : items) {
            total = total.add(li.getLineTotal());
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber(generatePONumber())
                .supplier(supplier)
                .orderDate(dto.getOrderDate())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(total)
                .company(company)
                .createdBy(user)
                .items(items)
                .build();

        PurchaseOrder saved = repo.save(po);
        repo.flush();
        vendorPayableService.createVendorPayableForPurchaseOrder(saved);
        purchaseInvoiceGenerationScheduler.schedulePurchaseInvoiceAfterCommit(saved.getId());
        return toDTO(saved);
    }

    public PurchaseOrderResponseDTO confirm(Long id) {
        PurchaseOrder po = getEntity(id);

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT purchase orders can be confirmed");
        }

        if (!vendorPayableService.isVendorPaymentSettledForPurchaseOrder(po.getId())) {
            throw new RuntimeException(
                    "Confirm vendor payment in Finance → Accounts Payable → Vendor Payments before releasing this PO to the supplier.");
        }

        po.setStatus(PurchaseOrderStatus.CONFIRMED);
        return toDTO(repo.save(po));
    }

    public PurchaseOrderResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public List<PurchaseOrderResponseDTO> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    public PurchaseOrderResponseDTO update(Long id, PurchaseOrderUpdateDTO dto) {

        PurchaseOrder po = getEntity(id);

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT purchase orders can be updated");
        }

        po.getItems().clear();

        BigDecimal total = BigDecimal.ZERO;

        List<PurchaseOrderItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow();

            PurchaseLinePricing.Resolved r = PurchaseLinePricing.resolveOrderLine(
                    item, i.getOtherUnitCost(), i.getUnitCost());
            BigDecimal lineTotal = r.appliedUnitCost()
                    .multiply(BigDecimal.valueOf(i.getQuantity()));

            return PurchaseOrderItem.builder()
                    .item(item)
                    .quantity(i.getQuantity())
                    .actualItemPrice(r.actualItemPrice())
                    .otherUnitCost(r.otherUnitCost())
                    .unitCost(r.appliedUnitCost())
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (PurchaseOrderItem li : items) {
            total = total.add(li.getLineTotal());
        }

        po.setOrderDate(dto.getOrderDate());
        // Keep the same managed collection instance for orphanRemoval/all-delete-orphan.
        po.getItems().clear();
        po.getItems().addAll(items);
        po.setTotalAmount(total);

        return toDTO(repo.save(po));
    }

    public PurchaseOrderResponseDTO cancel(Long id) {
        PurchaseOrder po = getEntity(id);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return toDTO(repo.save(po));
    }

    public PurchaseOrderResponseDTO archive(Long id) {
        PurchaseOrder po = getEntity(id);
        if (po.isArchived()) {
            return toDTO(po);
        }
        if (po.getStatus() != PurchaseOrderStatus.RECEIVED
                && po.getStatus() != PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException("Only RECEIVED or CANCELLED purchase orders can be archived");
        }
        po.setArchived(true);
        return toDTO(repo.save(po));
    }

    private PurchaseOrder getEntity(Long id) {
        return repo.findById(id)
                .filter(po -> po.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
    }

    private String generatePONumber() {
        return "PO-" + System.currentTimeMillis();
    }

    private PurchaseOrderResponseDTO toDTO(PurchaseOrder po) {
        return PurchaseOrderResponseDTO.builder()
                .id(po.getId())
                .orderNumber(po.getOrderNumber())
                .sourceRequisitionId(
                        po.getSourceRequisition() != null
                                ? po.getSourceRequisition().getId()
                                : null)
                .supplierId(po.getSupplier().getId())
                .supplierName(po.getSupplier().getVendorName())
                .orderDate(po.getOrderDate())
                .status(po.getStatus().name())
                .archived(po.isArchived())
                .createdAt(po.getCreatedAt().toString())
                .createdById(po.getCreatedBy().getId())
                .createdByName(po.getCreatedBy().getFullName())
                .totalAmount(po.getTotalAmount())
                .vendorPaymentSettled(vendorPayableService.isVendorPaymentSettledForPurchaseOrder(po.getId()))
                .items(
                        po.getItems().stream().map(i ->
                                PurchaseOrderItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .itemName(i.getItem().getName())
                                        .itemDescription(i.getItem().getDescription())
                                        .quantity(i.getQuantity())
                                        .actualItemPrice(i.getActualItemPrice())
                                        .otherUnitCost(i.getOtherUnitCost())
                                        .unitCost(i.getUnitCost())
                                        .unitPrice(i.getUnitCost())
                                        .lineTotal(i.getLineTotal())
                                        .build()
                        ).toList()
                )
                .build();
    }
}
