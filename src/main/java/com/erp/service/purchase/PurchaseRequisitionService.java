package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.purchase.*;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.purchase.PurchaseRequisitionCreateDTO;
import com.erp.dto.purchase.PurchaseRequisitionItemDTO;
import com.erp.dto.purchase.PurchaseRequisitionResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.CoaBalanceRules;
import com.erp.service.finance.VendorPayableService;
import com.erp.service.finance.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DepartmentRepository departmentRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final TransactionService transactionService;
    private final VendorPayableService vendorPayableService;
    private final AuthContext auth;

    public PurchaseRequisitionService(
            PurchaseRequisitionRepository repo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            VendorRepository vendorRepo,
            DepartmentRepository departmentRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            ChartOfAccountsRepository coaRepo,
            TransactionService transactionService,
            VendorPayableService vendorPayableService
    ) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.vendorRepo = vendorRepo;
        this.departmentRepo = departmentRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.coaRepo = coaRepo;
        this.transactionService = transactionService;
        this.vendorPayableService = vendorPayableService;
    }

    public PurchaseRequisitionResponseDTO create(PurchaseRequisitionCreateDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("At least one line item is required");
        }
        if (dto.getPreferredSupplierId() == null) {
            throw new RuntimeException("Preferred supplier is required");
        }
        if (dto.getDebitAccountId() == null || dto.getCreditAccountId() == null) {
            throw new RuntimeException("Debit and credit accounts are required");
        }
        if (dto.getDebitAccountId().equals(dto.getCreditAccountId())) {
            throw new RuntimeException("Debit and credit accounts cannot be the same");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        ChartOfAccounts debitAccount = resolveCoa(company.getId(), dto.getDebitAccountId());
        ChartOfAccounts creditAccount = resolveCoa(company.getId(), dto.getCreditAccountId());

        BigDecimal estimatedTotal = computeTotalFromItemDtos(dto.getItems());
        validatePostingBalances(debitAccount, creditAccount, estimatedTotal);

        Vendor supplier = vendorRepo.findById(dto.getPreferredSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        if (!supplier.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Supplier does not belong to this company");
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

        List<PurchaseRequisitionItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            return PurchaseRequisitionItem.builder()
                    .item(item)
                    .requestedQty(i.getRequestedQty())
                    .estimatedUnitCost(i.getEstimatedUnitCost())
                    .remarks(i.getRemarks())
                    .build();
        }).toList();

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .requisitionNumber("PR-" + System.currentTimeMillis())
                .status(PurchaseRequisitionStatus.DRAFT)
                .company(company)
                .requestedBy(requester)
                .preferredSupplier(supplier)
                .department(department)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
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
        if (pr.getFinanceTransactionId() != null) {
            throw new RuntimeException("This requisition already has a posted finance transaction");
        }

        User approver = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        pr.setApprovedBy(approver);
        pr.setApprovedAt(Instant.now());

        PurchaseOrder po = createPurchaseOrderFromRequisition(pr, approver);

        validatePostingBalances(pr.getDebitAccount(), pr.getCreditAccount(), po.getTotalAmount());

        CreateTransactionDTO txDto = CreateTransactionDTO.builder()
                .companyId(pr.getCompany().getId())
                .transactionType(TransactionService.TYPE_PURCHASE_REQUISITION)
                .transactionDate(LocalDate.now())
                .amount(po.getTotalAmount())
                .debitAccount(pr.getDebitAccount().getId())
                .creditAccount(pr.getCreditAccount().getId())
                .relatedId(pr.getId())
                .transactionDescription("PR " + pr.getRequisitionNumber() + " → PO " + po.getOrderNumber())
                .build();
        TransactionResponseDTO tx = transactionService.create(txDto);
        pr.setFinanceTransactionId(tx.getId());

        pr.setStatus(PurchaseRequisitionStatus.CONVERTED);
        pr.setConvertedAt(Instant.now());
        pr.setConvertedBy(approver);

        repo.save(pr);

        return toDTO(pr, po.getId());
    }

    public PurchaseRequisitionResponseDTO get(Long id) {
        return toDTO(getEntity(id), null);
    }

    public List<PurchaseRequisitionResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
                .stream()
                .map(pr -> toDTO(pr, null))
                .toList();
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
            BigDecimal unit = i.getEstimatedUnitCost() != null
                    ? i.getEstimatedUnitCost()
                    : (item.getCostPrice() != null ? item.getCostPrice() : null);
            if (unit == null) {
                throw new RuntimeException(
                        "Estimated unit cost or item cost price is required for item " + item.getId());
            }
            total = total.add(unit.multiply(BigDecimal.valueOf(i.getRequestedQty())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeTotalForRequisition(PurchaseRequisition pr) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequisitionItem pri : pr.getItems()) {
            BigDecimal unit = resolveUnitCost(pri, pri.getItem());
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
            BigDecimal unitCost = resolveUnitCost(pri, item);
            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(pri.getRequestedQty()));

            return PurchaseOrderItem.builder()
                    .item(item)
                    .quantity(pri.getRequestedQty())
                    .unitCost(unitCost)
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        BigDecimal total = poItems.stream()
                .map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber("PO-" + System.currentTimeMillis())
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
        vendorPayableService.createVendorPayableForPurchaseOrder(saved);
        return saved;
    }

    private BigDecimal resolveUnitCost(PurchaseRequisitionItem pri, Item item) {
        if (pri.getEstimatedUnitCost() != null) {
            return pri.getEstimatedUnitCost();
        }
        if (item.getCostPrice() != null) {
            return item.getCostPrice();
        }
        throw new RuntimeException(
                "Unit cost is required for item " + item.getId()
                        + ": set estimated unit cost on the requisition line or item cost price.");
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
                        .createdPurchaseOrderId(createdPurchaseOrderId)
                        .items(
                                pr.getItems().stream()
                                        .map(i -> new PurchaseRequisitionItemDTO(
                                                i.getItem().getId(),
                                                i.getRequestedQty(),
                                                i.getEstimatedUnitCost(),
                                                i.getRemarks()))
                                        .toList()
                        );

        if (pr.getPreferredSupplier() != null) {
            b.preferredSupplierId(pr.getPreferredSupplier().getId())
                    .preferredSupplierName(pr.getPreferredSupplier().getVendorName());
        }
        if (pr.getDepartment() != null) {
            b.departmentId(pr.getDepartment().getId())
                    .departmentName(pr.getDepartment().getDepartmentName());
        }
        if (pr.getRequestedBy() != null) {
            b.requestedById(pr.getRequestedBy().getId())
                    .requestedByName(pr.getRequestedBy().getFullName());
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
}
