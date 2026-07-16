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
import com.erp.dto.purchase.PurchaseRequisitionReviewDTO;
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
import com.erp.service.DocumentSequenceService;
import com.erp.service.finance.CompanyAccountingDefaultsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;
    private final PurchaseRequisitionDocumentRepository documentRepo;
    private final FileStorageService fileStorageService;
    private final PurchaseProcurementDuplicateGuard procurementDuplicateGuard;
    private final CompanyAccountingDefaultsService accountingDefaults;

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
            DocumentSequenceService documentSequenceService,
            PurchaseRequisitionDocumentRepository documentRepo,
            FileStorageService fileStorageService,
            PurchaseProcurementDuplicateGuard procurementDuplicateGuard,
            CompanyAccountingDefaultsService accountingDefaults
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
        this.documentSequenceService = documentSequenceService;
        this.documentRepo = documentRepo;
        this.fileStorageService = fileStorageService;
        this.procurementDuplicateGuard = procurementDuplicateGuard;
        this.accountingDefaults = accountingDefaults;
    }

    public PurchaseRequisitionResponseDTO create(PurchaseRequisitionCreateDTO dto) {
        validateCreateDto(dto);
        assertNoPendingProcurementForDto(dto, null);

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        var accounts = accountingDefaults.requirePurchaseAccounts(company.getId());
        accountingDefaults.assertDistinctAccounts(
                "Purchase requisition",
                accounts.debitAccountId(),
                accounts.creditAccountId());

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .requisitionNumber(documentSequenceService.generateNext("PR"))
                .status(PurchaseRequisitionStatus.DRAFT)
                .company(company)
                .build();

        applyDtoToRequisition(pr, dto, company, accounts.debitAccount(), accounts.creditAccount());
        return toDTO(repo.save(pr), null);
    }

    private void assertNoPendingProcurementForDto(PurchaseRequisitionCreateDTO dto, Long excludeRequisitionId) {
        List<Long> itemIds = dto.getItems().stream()
                .map(PurchaseRequisitionItemDTO::getItemId)
                .toList();
        procurementDuplicateGuard.assertNoPendingProcurement(
                auth.getCurrentCompanyId(),
                dto.getDeliveryWarehouseId(),
                itemIds,
                excludeRequisitionId);
    }

    private void assertNoPendingProcurementForRequisition(PurchaseRequisition pr, Long excludeRequisitionId) {
        if (pr.getDeliveryWarehouse() == null || pr.getItems() == null || pr.getItems().isEmpty()) {
            return;
        }
        List<Long> itemIds = pr.getItems().stream()
                .map(line -> line.getItem().getId())
                .toList();
        procurementDuplicateGuard.assertNoPendingProcurement(
                pr.getCompany().getId(),
                pr.getDeliveryWarehouse().getId(),
                itemIds,
                excludeRequisitionId);
    }

    private void validateCreateDto(PurchaseRequisitionCreateDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("At least one line item is required");
        }
        if (dto.getRequiredDeliveryDate() == null) {
            throw new RuntimeException("Required delivery date is required");
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
    }

    private void applyDtoToRequisition(
            PurchaseRequisition pr,
            PurchaseRequisitionCreateDTO dto,
            Company company,
            ChartOfAccounts debitAccount,
            ChartOfAccounts creditAccount
    ) {
        Vendor supplier = null;
        if (dto.getPreferredSupplierId() != null) {
            supplier = vendorRepo.findById(dto.getPreferredSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
            if (!supplier.getCompany().getId().equals(company.getId())) {
                throw new RuntimeException("Supplier does not belong to this company");
            }
        }

        User requester = pr.getRequestedBy();
        if (requester == null) {
            requester = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        }
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

        List<PurchaseRequisitionItem> items = buildItemsFromDto(dto);

        pr.setRequestedBy(requester);
        pr.setPreferredSupplier(supplier);
        pr.setSupplierAddress(
                supplier != null ? trimToNull(dto.getSupplierAddress()) : null);
        pr.setDepartment(department);
        pr.setDebitAccount(debitAccount);
        pr.setCreditAccount(creditAccount);
        pr.setRequestedDate(requestedDate);
        pr.setRequiredDeliveryDate(dto.getRequiredDeliveryDate());
        pr.setProjectCode(trimToNull(dto.getProjectCode()));
        pr.setRequisitionDescription(dto.getRequisitionDescription().trim());
        pr.setUrgency(urgency);
        pr.setDeliveryWarehouse(deliveryWarehouse);
        pr.setJustification(dto.getJustification().trim());
        pr.setNotes(trimToNull(dto.getNotes()));
        // Keep a mutable managed collection instance for Hibernate merge/update flows.
        if (pr.getItems() == null) {
            pr.setItems(new ArrayList<>());
        } else {
            pr.getItems().clear();
        }
        pr.getItems().addAll(items);
    }

    private List<PurchaseRequisitionItem> buildItemsFromDto(PurchaseRequisitionCreateDTO dto) {
        return dto.getItems().stream().map(i -> {
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
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    public PurchaseRequisitionResponseDTO submit(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT requisitions can be submitted");
        }
        if (pr.getDebitAccount() == null || pr.getCreditAccount() == null) {
            throw new RuntimeException("Debit and credit accounts are required before submit");
        }

        assertNoPendingProcurementForRequisition(pr, pr.getId());

        clearReviewFeedback(pr);
        pr.setStatus(PurchaseRequisitionStatus.SUBMITTED);
        return toDTO(repo.save(pr), null);
    }

    public PurchaseRequisitionResponseDTO update(Long id, PurchaseRequisitionCreateDTO dto) {
        validateCreateDto(dto);
        PurchaseRequisition pr = getEntity(id);
        if (pr.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw new RuntimeException(
                    "Only draft requisitions can be edited. Rejected requisitions must use Revise first.");
        }
        assertNoPendingProcurementForDto(dto, id);

        Company company = pr.getCompany();
        var accounts = accountingDefaults.requirePurchaseAccounts(company.getId());
        accountingDefaults.assertDistinctAccounts(
                "Purchase requisition",
                accounts.debitAccountId(),
                accounts.creditAccountId());

        applyDtoToRequisition(pr, dto, company, accounts.debitAccount(), accounts.creditAccount());
        return toDTO(repo.save(pr), null);
    }

    public PurchaseRequisitionResponseDTO reject(Long id, PurchaseRequisitionReviewDTO dto) {
        return returnToRequester(id, dto, PurchaseRequisitionReviewAction.REJECT);
    }

    public PurchaseRequisitionResponseDTO sendBack(Long id, PurchaseRequisitionReviewDTO dto) {
        return returnToRequester(id, dto, PurchaseRequisitionReviewAction.SEND_BACK);
    }

    public PurchaseRequisitionResponseDTO revise(Long id) {
        PurchaseRequisition pr = getEntity(id);
        if (pr.getStatus() != PurchaseRequisitionStatus.REJECTED) {
            throw new RuntimeException("Only REJECTED requisitions can be revised");
        }
        pr.setStatus(PurchaseRequisitionStatus.DRAFT);
        return toDTO(repo.save(pr), null);
    }

    private PurchaseRequisitionResponseDTO returnToRequester(
            Long id,
            PurchaseRequisitionReviewDTO dto,
            PurchaseRequisitionReviewAction action
    ) {
        if (dto.getComments() == null || dto.getComments().isBlank()) {
            throw new RuntimeException("Comments are required");
        }
        PurchaseRequisition pr = getEntity(id);
        if (pr.getStatus() != PurchaseRequisitionStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requisitions can be returned to the requester");
        }

        User reviewer = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        pr.setStatus(PurchaseRequisitionStatus.REJECTED);
        pr.setRejectionReason(dto.getComments().trim());
        pr.setReviewAction(action);
        pr.setRejectedBy(reviewer);
        pr.setRejectedAt(Instant.now());
        pr.setApprovedBy(null);
        pr.setApprovedAt(null);

        return toDTO(repo.save(pr), null);
    }

    private static void clearReviewFeedback(PurchaseRequisition pr) {
        pr.setRejectionReason(null);
        pr.setReviewAction(null);
        pr.setRejectedBy(null);
        pr.setRejectedAt(null);
    }

    /**
     * Approves a submitted requisition and creates a draft purchase order from it in one step.
     */
    public PurchaseRequisitionResponseDTO approve(Long id) {
        PurchaseRequisition pr = getEntity(id);

        if (pr.getStatus() != PurchaseRequisitionStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requisitions can be approved");
        }
        if (pr.getDebitAccount() == null || pr.getCreditAccount() == null) {
            throw new RuntimeException("Debit and credit accounts are required");
        }

        User approver = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        pr.setApprovedBy(approver);
        pr.setApprovedAt(Instant.now());

        PurchaseOrder po = createPurchaseOrderFromRequisition(pr, approver);

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
        if (pr.getStatus() != PurchaseRequisitionStatus.DRAFT
                && pr.getStatus() != PurchaseRequisitionStatus.REJECTED) {
            throw new RuntimeException("Documents can only be uploaded while the requisition is draft or awaiting revision");
        }
        User uploader = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        FileUploadResult upload = fileStorageService.upload(
                file,
                FileCategory.PURCHASE_REQUISITION_DOCUMENT,
                pr.getId().toString(),
                false,
                auth.getCurrentCompanyId()
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
                .supplier(null)
                .orderDate(LocalDate.now())
                .requiredDeliveryDate(pr.getRequiredDeliveryDate())
                .status(PurchaseOrderStatus.DRAFT)
                .totalAmount(total)
                .company(company)
                .createdBy(actor)
                .requestedBy(pr.getRequestedBy())
                .items(poItems)
                .sourceRequisition(pr)
                .build();

        PurchaseOrder saved = purchaseOrderRepo.save(po);
        purchaseOrderRepo.flush();
        return saved;
    }

    private PurchaseRequisitionResponseDTO toDTO(PurchaseRequisition pr, Long createdPurchaseOrderId) {
        List<PurchaseRequisitionItemDTO> itemDTOs = pr.getItems().stream()
                .map(i -> {
                    BigDecimal unitCost = i.getEstimatedUnitCost();
                    BigDecimal qty = i.getRequestedQty() != null
                            ? BigDecimal.valueOf(i.getRequestedQty()) : BigDecimal.ZERO;
                    BigDecimal lineTotal = (unitCost != null)
                            ? unitCost.multiply(qty).setScale(2, RoundingMode.HALF_UP)
                            : null;
                    return new PurchaseRequisitionItemDTO(
                            i.getItem().getId(),
                            i.getItem().getName(),
                            i.getRequestedQty(),
                            i.getActualItemPrice(),
                            i.getOtherUnitCost(),
                            i.getEstimatedUnitCost(),
                            lineTotal,
                            i.getRemarks());
                })
                .toList();

        BigDecimal totalAmount = itemDTOs.stream()
                .filter(d -> d.getEstimatedTotal() != null)
                .map(PurchaseRequisitionItemDTO::getEstimatedTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                        .totalAmount(totalAmount.compareTo(BigDecimal.ZERO) > 0 ? totalAmount : null)
                        .items(itemDTOs)
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
        b.rejectionReason(pr.getRejectionReason());
        if (pr.getReviewAction() != null) {
            b.reviewAction(pr.getReviewAction().name());
        }
        b.rejectedAt(pr.getRejectedAt());
        if (pr.getRejectedBy() != null) {
            b.rejectedById(pr.getRejectedBy().getId())
                    .rejectedByName(pr.getRejectedBy().getFullName());
        }
        b.requestedDate(pr.getRequestedDate())
                .requiredDeliveryDate(pr.getRequiredDeliveryDate())
                .projectCode(pr.getProjectCode())
                .requisitionDescription(pr.getRequisitionDescription())
                .urgency(pr.getUrgency() != null ? pr.getUrgency().name() : null)
                .justification(pr.getJustification())
                .notes(pr.getNotes());
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
            purchaseOrderRepo
                    .findById(poId)
                    .ifPresent(po -> b.createdPurchaseOrderNumber(po.getOrderNumber()));
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
