package com.erp.service.purchase;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderItem;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.purchase.PurchaseOrderAssignSupplierDTO;
import com.erp.dto.purchase.PurchaseOrderCreateDTO;
import com.erp.dto.purchase.PurchaseOrderItemDTO;
import com.erp.dto.purchase.PurchaseOrderPostingPreviewDTO;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.purchase.PurchaseOrderUpdateDTO;
import com.erp.service.finance.CoaBalanceRules;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.PurchaseInvoiceGenerationScheduler;
import com.erp.service.finance.TransactionService;
import com.erp.service.finance.VendorPayableService;
import com.erp.service.DocumentSequenceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TransactionService transactionService;
    private final ChartOfAccountsRepository coaRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public PurchaseOrderService(
            PurchaseOrderRepository repo,
            VendorRepository vendorRepo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            VendorPayableService vendorPayableService,
            @Lazy PurchaseInvoiceGenerationScheduler purchaseInvoiceGenerationScheduler,
            TransactionService transactionService,
            ChartOfAccountsRepository coaRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService
    ) {
        this.repo = repo;
        this.vendorRepo = vendorRepo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.vendorPayableService = vendorPayableService;
        this.purchaseInvoiceGenerationScheduler = purchaseInvoiceGenerationScheduler;
        this.transactionService = transactionService;
        this.coaRepo = coaRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
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

    public PurchaseOrderResponseDTO assignSupplier(Long id, PurchaseOrderAssignSupplierDTO dto) {
        if (dto.getSupplierId() == null) {
            throw new RuntimeException("Supplier is required");
        }
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Supplier can only be assigned on DRAFT purchase orders");
        }
        applyDraftSupplierChange(po, dto.getSupplierId());
        return toDTO(repo.save(po));
    }

    private void applyDraftSupplierChange(PurchaseOrder po, Long supplierId) {
        Long companyId = po.getCompany().getId();
        Vendor supplier = vendorRepo.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        if (!supplier.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Supplier does not belong to this company");
        }
        boolean changed = po.getSupplier() == null
                || !po.getSupplier().getId().equals(supplierId);
        po.setSupplier(supplier);
        if (changed) {
            repo.flush();
            vendorPayableService.createVendorPayableForPurchaseOrder(po);
            purchaseInvoiceGenerationScheduler.schedulePurchaseInvoiceAfterCommit(po.getId());
        }
    }

    public PurchaseOrderResponseDTO confirm(Long id) {
        PurchaseOrder po = getEntity(id);

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT purchase orders can be confirmed");
        }
        if (po.getSupplier() == null) {
            throw new RuntimeException("Assign a supplier before releasing this purchase order");
        }

        postEncumbranceOnConfirm(po);

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

        if (dto.getSupplierId() != null) {
            applyDraftSupplierChange(po, dto.getSupplierId());
        } else if (po.getSupplier() == null) {
            throw new RuntimeException("Supplier is required on draft purchase orders");
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
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return toDTO(po);
        }
        releaseEncumbranceOnCancel(po);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return toDTO(repo.save(po));
    }

    private void postEncumbranceOnConfirm(PurchaseOrder po) {
        if (po.getFinanceTransactionId() != null) {
            return;
        }
        if (transactionService.hasPurchaseOrderVendorPaymentPosting(po.getId())) {
            return;
        }
        BigDecimal amount = po.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Purchase order total must be positive to release funds");
        }
        PostingAccounts accounts = resolvePostingAccounts(po);
        Long companyId = po.getCompany().getId();
        TransactionResponseDTO tx = transactionService.createPurchaseOrderEncumbrance(
                companyId,
                po.getId(),
                amount,
                accounts.debitAccountId(),
                accounts.creditAccountId(),
                po.getOrderNumber());
        po.setFinanceTransactionId(tx.getId());
    }

    public PurchaseOrderPostingPreviewDTO getPostingPreview(Long id, String action) {
        PurchaseOrder po = getEntity(id);
        String normalized = action == null ? "" : action.trim().toLowerCase();
        if (!"release".equals(normalized) && !"cancel".equals(normalized)) {
            throw new RuntimeException("Preview action must be 'release' or 'cancel'");
        }

        BigDecimal amount = po.getTotalAmount() == null
                ? BigDecimal.ZERO
                : po.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        boolean fundsCommitted = isFundsCommitted(po);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return PurchaseOrderPostingPreviewDTO.builder()
                    .action(normalized)
                    .amount(amount)
                    .sufficientFunds(false)
                    .fundsAlreadyCommitted(fundsCommitted)
                    .willReleaseCommittedFunds(false)
                    .summary("Purchase order total must be positive before funds can be committed or released.")
                    .insufficientFundsMessage("Purchase order total must be positive.")
                    .build();
        }

        PostingAccounts accounts = resolvePostingAccounts(po);
        ChartOfAccounts debit = coaRepo.findById(accounts.debitAccountId())
                .orElseThrow(() -> new RuntimeException("Debit account not found"));
        ChartOfAccounts credit = coaRepo.findById(accounts.creditAccountId())
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        BigDecimal debitBefore = balanceOf(debit);
        BigDecimal creditBefore = balanceOf(credit);

        BigDecimal debitDelta;
        BigDecimal creditDelta;
        String summary;

        if ("cancel".equals(normalized)) {
            if (!fundsCommitted) {
                return PurchaseOrderPostingPreviewDTO.builder()
                        .action(normalized)
                        .amount(amount)
                        .debitAccountId(debit.getId())
                        .debitAccountCode(debit.getAccountCode())
                        .debitAccountName(debit.getAccountName())
                        .debitBalanceBefore(debitBefore)
                        .debitBalanceAfter(debitBefore)
                        .creditAccountId(credit.getId())
                        .creditAccountCode(credit.getAccountCode())
                        .creditAccountName(credit.getAccountName())
                        .creditBalanceBefore(creditBefore)
                        .creditBalanceAfter(creditBefore)
                        .sufficientFunds(true)
                        .fundsAlreadyCommitted(false)
                        .willReleaseCommittedFunds(false)
                        .summary("No funds have been committed for this order yet. Cancelling will not change account balances.")
                        .build();
            }
            debitDelta = amount;
            creditDelta = amount.negate();
            summary = "Committed funds will be released: debit account balance increases and credit account balance decreases.";
        } else {
            if (fundsCommitted) {
                return PurchaseOrderPostingPreviewDTO.builder()
                        .action(normalized)
                        .amount(amount)
                        .debitAccountId(debit.getId())
                        .debitAccountCode(debit.getAccountCode())
                        .debitAccountName(debit.getAccountName())
                        .debitBalanceBefore(debitBefore)
                        .debitBalanceAfter(debitBefore)
                        .creditAccountId(credit.getId())
                        .creditAccountCode(credit.getAccountCode())
                        .creditAccountName(credit.getAccountName())
                        .creditBalanceBefore(creditBefore)
                        .creditBalanceAfter(creditBefore)
                        .sufficientFunds(true)
                        .fundsAlreadyCommitted(true)
                        .willReleaseCommittedFunds(false)
                        .summary("Funds are already committed for this purchase order.")
                        .build();
            }
            if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
                throw new RuntimeException("Only draft purchase orders can be released");
            }
            debitDelta = amount.negate();
            creditDelta = amount;
            summary = "Releasing will commit funds: debit account decreases and credit account increases by the order total.";
        }

        BigDecimal debitAfter = debitBefore.add(debitDelta);
        BigDecimal creditAfter = creditBefore.add(creditDelta);

        boolean sufficient = true;
        String insufficientMessage = null;
        try {
            CoaBalanceRules.assertSufficientBalance(debit, debitDelta);
            CoaBalanceRules.assertSufficientBalance(credit, creditDelta);
        } catch (ResponseStatusException ex) {
            sufficient = false;
            insufficientMessage = ex.getReason();
        }

        return PurchaseOrderPostingPreviewDTO.builder()
                .action(normalized)
                .amount(amount)
                .debitAccountId(debit.getId())
                .debitAccountCode(debit.getAccountCode())
                .debitAccountName(debit.getAccountName())
                .debitBalanceBefore(debitBefore)
                .debitBalanceAfter(debitAfter)
                .creditAccountId(credit.getId())
                .creditAccountCode(credit.getAccountCode())
                .creditAccountName(credit.getAccountName())
                .creditBalanceBefore(creditBefore)
                .creditBalanceAfter(creditAfter)
                .sufficientFunds(sufficient)
                .insufficientFundsMessage(insufficientMessage)
                .fundsAlreadyCommitted(fundsCommitted)
                .willReleaseCommittedFunds("cancel".equals(normalized) && fundsCommitted)
                .summary(summary)
                .build();
    }

    private static BigDecimal balanceOf(ChartOfAccounts coa) {
        return coa.getBalance() == null ? BigDecimal.ZERO : coa.getBalance();
    }

    private boolean isFundsCommitted(PurchaseOrder po) {
        return po.getFinanceTransactionId() != null
                || transactionService.hasPurchaseOrderEncumbrance(po.getId());
    }

    private void releaseEncumbranceOnCancel(PurchaseOrder po) {
        if (!isFundsCommitted(po)) {
            return;
        }
        BigDecimal amount = po.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PostingAccounts accounts = resolvePostingAccounts(po);
        try {
            transactionService.createPurchaseOrderCancelReversal(
                    po.getCompany().getId(),
                    po.getId(),
                    amount,
                    accounts.debitAccountId(),
                    accounts.creditAccountId(),
                    po.getOrderNumber());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                throw ex;
            }
        }
    }

    private record PostingAccounts(Long debitAccountId, Long creditAccountId) {}

    private PostingAccounts resolvePostingAccounts(PurchaseOrder po) {
        PurchaseRequisition pr = po.getSourceRequisition();
        if (pr != null && pr.getDebitAccount() != null && pr.getCreditAccount() != null) {
            return new PostingAccounts(pr.getDebitAccount().getId(), pr.getCreditAccount().getId());
        }
        Company company = po.getCompany();
        Long debitId = company.getDefaultPurchaseDebitAccountId();
        Long creditId = company.getDefaultPurchaseCreditAccountId();
        if (debitId == null || creditId == null) {
            throw new RuntimeException(
                    "Set company default purchase debit and credit accounts, or create this PO from a requisition with posting accounts.");
        }
        ChartOfAccounts debit = coaRepo.findById(debitId)
                .orElseThrow(() -> new RuntimeException("Default purchase debit account not found"));
        ChartOfAccounts credit = coaRepo.findById(creditId)
                .orElseThrow(() -> new RuntimeException("Default purchase credit account not found"));
        if (!debit.getCompany().getId().equals(company.getId())
                || !credit.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Default purchase accounts do not belong to this company");
        }
        if (debitId.equals(creditId)) {
            throw new RuntimeException("Default purchase debit and credit accounts cannot be the same");
        }
        return new PostingAccounts(debitId, creditId);
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
        return documentSequenceService.generateNext("PO");
    }

    private PurchaseOrderResponseDTO toDTO(PurchaseOrder po) {
        return PurchaseOrderResponseDTO.builder()
                .id(po.getId())
                .orderNumber(po.getOrderNumber())
                .sourceRequisitionId(
                        po.getSourceRequisition() != null
                                ? po.getSourceRequisition().getId()
                                : null)
                .supplierId(po.getSupplier() != null ? po.getSupplier().getId() : null)
                .supplierName(po.getSupplier() != null ? po.getSupplier().getVendorName() : null)
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
