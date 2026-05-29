package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.purchase.*;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.purchase.PurchaseRequisitionCreateDTO;
import com.erp.dto.purchase.PurchaseRequisitionDocumentDTO;
import com.erp.dto.purchase.PurchaseRequisitionItemDTO;
import com.erp.dto.purchase.PurchaseRequisitionResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionDocumentRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.service.file.FileStorageService;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.CoaBalanceRules;
import com.erp.service.finance.PurchaseInvoiceGenerationScheduler;
import com.erp.service.finance.VendorPayableService;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PurchaseRequisitionService {

    private final PurchaseRequisitionRepository repo;
    private final ItemRepository itemRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final VendorRepository vendorRepo;
    private final WarehouseRepository warehouseRepo;
    private final DepartmentRepository departmentRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final VendorPayableService vendorPayableService;
    private final PurchaseInvoiceGenerationScheduler purchaseInvoiceGenerationScheduler;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;
    private final PurchaseRequisitionDocumentRepository documentRepo;
    private final FileStorageService fileStorageService;

    public PurchaseRequisitionService(
            PurchaseRequisitionRepository repo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            VendorRepository vendorRepo,
            WarehouseRepository warehouseRepo,
            DepartmentRepository departmentRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            ChartOfAccountsRepository coaRepo,
            VendorPayableService vendorPayableService,
            PurchaseInvoiceGenerationScheduler purchaseInvoiceGenerationScheduler,
            DocumentSequenceService documentSequenceService,
            PurchaseRequisitionDocumentRepository documentRepo,
            FileStorageService fileStorageService
    ) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.vendorRepo = vendorRepo;
        this.warehouseRepo = warehouseRepo;
        this.departmentRepo = departmentRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.coaRepo = coaRepo;
        this.vendorPayableService = vendorPayableService;
        this.purchaseInvoiceGenerationScheduler = purchaseInvoiceGenerationScheduler;
        this.documentSequenceService = documentSequenceService;
        this.documentRepo = documentRepo;
        this.fileStorageService = fileStorageService;
    }

    public PurchaseRequisitionResponseDTO create(PurchaseRequisitionCreateDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("At least one line item is required");
        }

        if (dto.getDebitAccountId() == null || dto.getCreditAccountId() == null) {
            throw new RuntimeException("Debit and credit accounts are required");
        }
        if (dto.getDebitAccountId().equals(dto.getCreditAccountId())) {
            throw new RuntimeException("Debit and credit accounts cannot be the same");
        }
        if (dto.getRequiredDeliveryDate() == null) {
            throw new RuntimeException("Required delivery date is required");
        }
        if (dto.getRequiredByDate() == null) {
            throw new RuntimeException("Required-by date is required");
        }
        if (dto.getRequisitionDescription() == null || dto.getRequisitionDescription().isBlank()) {
            throw new RuntimeException("Requisition description is required");
        }
        if (dto.getJustification() == null || dto.getJustification().isBlank()) {
            throw new RuntimeException("Justification is required");
        }
        if (dto.getDeliveryWarehouseId() == null) {
            throw new RuntimeException("Delivery warehouse is required");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        ChartOfAccounts debitAccount = resolveCoa(company.getId(), dto.getDebitAccountId());
        ChartOfAccounts creditAccount = resolveCoa(company.getId(), dto.getCreditAccountId());

        BigDecimal estimatedTotal = computeTotalFromItemDtos(dto.getItems());
        validatePostingBalances(debitAccount, creditAccount, estimatedTotal);

        Vendor supplier = null;
        if (dto.getPreferredSupplierId() != null) {
            supplier = vendorRepo.findById(dto.getPreferredSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
            if (!supplier.getCompany().getId().equals(company.getId())) {
                throw new RuntimeException("Supplier does not belong to this company");
            }
        }

        User requester = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        if (dto.getRequestedByUserId() != null) {
            requester = userRepo.findById(dto.getRequestedByUserId())
                    .orElseThrow(() -> new RuntimeException("Requested-by user not found"));
        }

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepo.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            if (!department.getCompany().getId().equals(company.getId())) {
                throw new RuntimeException("Department does not belong to this company");
            }
        }

        Warehouse deliveryWarehouse = warehouseRepo.findById(dto.getDeliveryWarehouseId())
                .orElseThrow(() -> new RuntimeException("Delivery warehouse not found"));
        if (!deliveryWarehouse.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Delivery warehouse does not belong to this company");
        }

        LocalDate requestedDate = dto.getRequestedDate() != null ? dto.getRequestedDate() : LocalDate.now();
        PurchaseRequisitionUrgency urgency = resolveUrgency(dto.getUrgency());

        List<PurchaseRequisitionItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            PurchaseLinePricing.Resolved r = PurchaseLinePricing.resolveRequisitionLine(
                    item, i.getOtherUnitCost(), i.getEstimatedUnitCost());

            return PurchaseRequisitionItem.builder()
                    .item(item)
                    .requestedQty(i.getRequestedQty())
                    .actualItemPrice(r.actualItemPrice())
                    .otherUnitCost(r.otherUnitCost())
                    .estimatedUnitCost(r.appliedUnitCost())
                    .remarks(i.getRemarks())
                    .build();
        }).toList();

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .requisitionNumber(documentSequenceService.generateNext("PR"))
                .status(PurchaseRequisitionStatus.DRAFT)
                .company(company)
                .requestedBy(requester)
                .preferredSupplier(supplier)
                .supplierAddress(dto.getSupplierAddress())
                .department(department)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .requestedDate(requestedDate)
                .requiredDeliveryDate(dto.getRequiredDeliveryDate())
                .projectCode(trimToNull(dto.getProjectCode()))
                .requisitionDescription(dto.getRequisitionDescription().trim())
                .urgency(urgency)
                .requiredByDate(dto.getRequiredByDate())
                .deliveryWarehouse(deliveryWarehouse)
                .justification(dto.getJustification().trim())
                .items(items)
                .build();

        return toDTO(repo.save(pr), null);
    }

    public PurchaseRequisitionResponseDTO submit(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT requisitions can be submitted");
        }
        if (pr.getPreferredSupplier() == null) {
            throw new RuntimeException("Preferred supplier is required before submit");
        }
        if (pr.getDebitAccount() == null || pr.getCreditAccount() == null) {
            throw new RuntimeException("Debit and credit accounts are required before submit");
        }

        BigDecimal total = computeTotalForRequisition(pr);
        validatePostingBalances(pr.getDebitAccount(), pr.getCreditAccount(), total);

        pr.setStatus(PurchaseRequisitionStatus.SUBMITTED);
        return toDTO(repo.save(pr), null);
    }

    /**
     * Approves a submitted requisition and creates a draft purchase order from it in one step.
     */
    public PurchaseRequisitionResponseDTO approve(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requisitions can be approved");
        }
        if (pr.getPreferredSupplier() == null) {
            throw new RuntimeException("Preferred supplier is required");
        }
        if (pr.getDebitAccount() == null || pr.getCreditAccount() == null) {
            throw new RuntimeException("Debit and credit accounts are required");
        }

        User approver = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        pr.setApprovedBy(approver);
        pr.setApprovedAt(Instant.now());

        PurchaseOrder po = createPurchaseOrderFromRequisition(pr, approver);

        validatePostingBalances(pr.getDebitAccount(), pr.getCreditAccount(), po.getTotalAmount());

        pr.setStatus(PurchaseRequisitionStatus.CONVERTED);
        pr.setConvertedAt(Instant.now());
        pr.setConvertedBy(approver);

        repo.save(pr);

        return toDTO(pr, po.getId());
    }

    public PurchaseRequisitionResponseDTO archive(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.CONVERTED && pr.getStatus() != PurchaseRequisitionStatus.REJECTED) {
            throw new RuntimeException("Only CONVERTED or REJECTED requisitions can be archived");
        }

        pr.setArchived(true);
        return toDTO(repo.save(pr), null);
    }

    public PurchaseRequisitionResponseDTO get(Long id) {
        return toDTO(getEntity(id), null);
    }

    public List<PurchaseRequisitionResponseDTO> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream()
                .map(pr -> toDTO(pr, null))
                .toList();
    }

    public PurchaseRequisitionDocumentDTO uploadDocument(Long requisitionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }
        PurchaseRequisition pr = getEntity(requisitionId);
        User uploader = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        FileUploadResult upload = fileStorageService.upload(
                file,
                FileCategory.PURCHASE_REQUISITION_DOCUMENT,
                pr.getId().toString(),
                false
        );

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "document";
        }

        PurchaseRequisitionDocument doc = PurchaseRequisitionDocument.builder()
                .requisition(pr)
                .fileName(originalName)
                .blobPath(upload.getBlobPath())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .uploadedBy(uploader)
                .build();

        return toDocumentDTO(documentRepo.save(doc));
    }

    public List<PurchaseRequisitionDocumentDTO> listDocuments(Long requisitionId) {
        getEntity(requisitionId);
        return documentRepo.findByRequisitionIdOrderByUploadedAtDesc(requisitionId)
                .stream()
                .map(this::toDocumentDTO)
                .toList();
    }

    public void deleteDocument(Long requisitionId, Long documentId) {
        getEntity(requisitionId);
        PurchaseRequisitionDocument doc = documentRepo
                .findByIdAndRequisitionId(documentId, requisitionId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        documentRepo.delete(doc);
    }

    private static PurchaseRequisitionUrgency resolveUrgency(String urgency) {
        if (urgency == null || urgency.isBlank()) {
            return PurchaseRequisitionUrgency.NORMAL;
        }
        try {
            return PurchaseRequisitionUrgency.valueOf(urgency.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid urgency: " + urgency);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private ChartOfAccounts resolveCoa(Long companyId, Long accountId) {
        ChartOfAccounts a = coaRepo.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!a.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Account does not belong to this company");
        }
        return a;
    }

    /**
     * Same deltas as {@link TransactionService#applyPostingToCoa}: debit -= amount, credit += amount.
     */
    private void validatePostingBalances(ChartOfAccounts debit, ChartOfAccounts credit, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Requisition total must be positive for account validation");
        }
        if (debit.getId().equals(credit.getId())) {
            throw new RuntimeException("Debit and credit accounts cannot be the same");
        }
        CoaBalanceRules.assertSufficientBalance(debit, amount.negate());
        CoaBalanceRules.assertSufficientBalance(credit, amount);
    }

    private BigDecimal computeTotalFromItemDtos(List<PurchaseRequisitionItemDTO> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequisitionItemDTO i : lines) {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            PurchaseLinePricing.Resolved r = PurchaseLinePricing.resolveRequisitionLine(
                    item, i.getOtherUnitCost(), i.getEstimatedUnitCost());
            total = total.add(
                    r.appliedUnitCost().multiply(BigDecimal.valueOf(i.getRequestedQty())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeTotalForRequisition(PurchaseRequisition pr) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequisitionItem pri : pr.getItems()) {
            BigDecimal unit = pri.getEstimatedUnitCost();
            if (unit == null) {
                unit = PurchaseLinePricing.resolveRequisitionLine(
                                pri.getItem(), pri.getOtherUnitCost(), null)
                        .appliedUnitCost();
            }
            total = total.add(unit.multiply(BigDecimal.valueOf(pri.getRequestedQty())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private PurchaseRequisition getEntity(Long id) {
        return repo.findById(id)
                .filter(pr -> pr.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Requisition not found"));
    }

    private PurchaseOrder createPurchaseOrderFromRequisition(PurchaseRequisition pr, User actor) {
        Vendor supplier = pr.getPreferredSupplier();
        Company company = pr.getCompany();

        List<PurchaseOrderItem> poItems = pr.getItems().stream().map(pri -> {
            Item item = pri.getItem();
            BigDecimal applied = pri.getEstimatedUnitCost();
            BigDecimal actualSnap = pri.getActualItemPrice();
            BigDecimal other = pri.getOtherUnitCost();
            if (applied == null || actualSnap == null) {
                PurchaseLinePricing.Resolved r =
                        PurchaseLinePricing.resolveRequisitionLine(item, other, applied);
                applied = r.appliedUnitCost();
                if (actualSnap == null) {
                    actualSnap = r.actualItemPrice();
                }
            }
            BigDecimal lineTotal = applied.multiply(BigDecimal.valueOf(pri.getRequestedQty()));

            return PurchaseOrderItem.builder()
                    .item(item)
                    .quantity(pri.getRequestedQty())
                    .actualItemPrice(actualSnap)
                    .otherUnitCost(other)
                    .unitCost(applied)
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        BigDecimal total = poItems.stream()
                .map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber(documentSequenceService.generateNext("PO"))
                .supplier(supplier)
                .orderDate(LocalDate.now())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(total)
                .company(company)
                .createdBy(actor)
                .items(poItems)
                .sourceRequisition(pr)
                .build();

        PurchaseOrder saved = purchaseOrderRepo.save(po);
        purchaseOrderRepo.flush();
        vendorPayableService.createVendorPayableForPurchaseOrder(saved);
        purchaseInvoiceGenerationScheduler.schedulePurchaseInvoiceAfterCommit(saved.getId());
        return saved;
    }

    private PurchaseRequisitionResponseDTO toDTO(PurchaseRequisition pr, Long createdPurchaseOrderId) {
        PurchaseRequisitionResponseDTO.PurchaseRequisitionResponseDTOBuilder b =
                PurchaseRequisitionResponseDTO.builder()
                        .id(pr.getId())
                        .requisitionNumber(pr.getRequisitionNumber())
                        .status(pr.getStatus().name())
                        .createdAt(pr.getCreatedAt())
                        .approvedAt(pr.getApprovedAt())
                        .convertedAt(pr.getConvertedAt())
                        .archived(pr.isArchived())
                        .createdPurchaseOrderId(createdPurchaseOrderId)
                        .items(
                                pr.getItems().stream()
                                        .map(i -> new PurchaseRequisitionItemDTO(
                                                i.getItem().getId(),
                                                i.getRequestedQty(),
                                                i.getActualItemPrice(),
                                                i.getOtherUnitCost(),
                                                i.getEstimatedUnitCost(),
                                                i.getRemarks()))
                                        .toList()
                        )
                        .documents(
                                documentRepo.findByRequisitionIdOrderByUploadedAtDesc(pr.getId())
                                        .stream()
                                        .map(this::toDocumentDTO)
                                        .toList()
                        );

        if (pr.getPreferredSupplier() != null) {
            b.preferredSupplierId(pr.getPreferredSupplier().getId())
                    .preferredSupplierName(pr.getPreferredSupplier().getVendorName())
                    .supplierAddress(pr.getSupplierAddress());
        }
        if (pr.getDepartment() != null) {
            b.departmentId(pr.getDepartment().getId())
                    .departmentName(pr.getDepartment().getDepartmentName());
        }
        if (pr.getRequestedBy() != null) {
            b.requestedById(pr.getRequestedBy().getId())
                    .requestedByName(pr.getRequestedBy().getFullName());
        }
        b.requestedDate(pr.getRequestedDate())
                .requiredDeliveryDate(pr.getRequiredDeliveryDate())
                .projectCode(pr.getProjectCode())
                .requisitionDescription(pr.getRequisitionDescription())
                .urgency(pr.getUrgency() != null ? pr.getUrgency().name() : null)
                .requiredByDate(pr.getRequiredByDate())
                .justification(pr.getJustification());
        if (pr.getDeliveryWarehouse() != null) {
            b.deliveryWarehouseId(pr.getDeliveryWarehouse().getId())
                    .deliveryWarehouseName(pr.getDeliveryWarehouse().getName());
        }
        if (pr.getDebitAccount() != null) {
            b.debitAccountId(pr.getDebitAccount().getId())
                    .debitAccountName(pr.getDebitAccount().getAccountName());
        }
        if (pr.getCreditAccount() != null) {
            b.creditAccountId(pr.getCreditAccount().getId())
                    .creditAccountName(pr.getCreditAccount().getAccountName());
        }
        b.financeTransactionId(pr.getFinanceTransactionId());

        Long poId = createdPurchaseOrderId;
        if (poId == null && pr.getStatus() == PurchaseRequisitionStatus.CONVERTED) {
            poId = purchaseOrderRepo
                    .findBySourceRequisition_Id(pr.getId())
                    .map(PurchaseOrder::getId)
                    .orElse(null);
        }
        if (poId != null) {
            b.createdPurchaseOrderId(poId);
        }

        return b.build();
    }

    private PurchaseRequisitionDocumentDTO toDocumentDTO(PurchaseRequisitionDocument doc) {
        PurchaseRequisitionDocumentDTO.PurchaseRequisitionDocumentDTOBuilder b =
                PurchaseRequisitionDocumentDTO.builder()
                        .id(doc.getId())
                        .fileName(doc.getFileName())
                        .contentType(doc.getContentType())
                        .fileSizeBytes(doc.getFileSizeBytes())
                        .uploadedAt(doc.getUploadedAt())
                        .downloadUrl(resolveDocumentUrl(doc.getBlobPath()));
        if (doc.getUploadedBy() != null) {
            b.uploadedById(doc.getUploadedBy().getId())
                    .uploadedByName(doc.getUploadedBy().getFullName());
        }
        return b.build();
    }

    private String resolveDocumentUrl(String blobPath) {
        if (blobPath == null || blobPath.isBlank()) {
            return null;
        }
        if (blobPath.startsWith("http://") || blobPath.startsWith("https://")) {
            return blobPath;
        }
        return fileStorageService.getPrivateSasUrl(blobPath);
    }
}
