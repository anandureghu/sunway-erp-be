package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.finance.Transaction;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.StockVariance;
import com.erp.domain.inventory.StockVarianceStatus;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.domain.security.Role;
import com.erp.dto.inventory.StockVarianceCreateDTO;
import com.erp.dto.inventory.StockVarianceResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.StockVarianceRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.dto.hr.ProcessAccountPair;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.CompanyAccountingDefaultsService;
import com.erp.service.finance.TransactionService;
import com.erp.service.security.PermissionCheckService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class StockVarianceService {

    private final StockVarianceRepository repo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final ItemWarehouseStockService stockService;
    private final StockBatchService stockBatchService;
    private final TransactionService transactionService;
    private final PermissionCheckService permissionCheckService;
    private final CompanyAccountingDefaultsService accountingDefaults;

    public StockVarianceService(
            StockVarianceRepository repo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            ItemWarehouseStockService stockService,
            StockBatchService stockBatchService,
            TransactionService transactionService,
            PermissionCheckService permissionCheckService,
            CompanyAccountingDefaultsService accountingDefaults
    ) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.stockService = stockService;
        this.stockBatchService = stockBatchService;
        this.transactionService = transactionService;
        this.permissionCheckService = permissionCheckService;
        this.accountingDefaults = accountingDefaults;
    }

    public StockVarianceResponseDTO create(StockVarianceCreateDTO dto) {
        Long companyId = auth.getCurrentCompanyId();
        User creator = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }

        ComputedVariance c = computeVarianceFields(dto, companyId);
        Company company = companyRepo.findById(companyId).orElseThrow();
        LocalDate varianceDate = parseDate(dto.getVarianceDate());

        StockVariance variance = StockVariance.builder()
                .company(company)
                .item(c.item)
                .fromWarehouse(c.fromWarehouse)
                .toWarehouse(c.toWarehouse)
                .varianceType(c.varianceType)
                .varianceStatus(StockVarianceStatus.PENDING)
                .adjustmentMode(c.adjustmentMode)
                .quantityBefore(c.quantityBefore)
                .quantityAfter(c.quantityAfter)
                .adjustmentQuantity(c.adjustmentQuantity)
                .transferQuantity(c.transferQuantity)
                .reason(dto.getReason().trim())
                .notes(dto.getNotes())
                .varianceDate(varianceDate)
                .createdBy(creator)
                .createdAt(Instant.now())
                .build();

        return toDTO(repo.save(variance));
    }

    /**
     * Revises and resubmits a variance that was previously sent back to its requester,
     * putting it back into {@link StockVarianceStatus#PENDING}. Only the original
     * requester may resubmit; the send-back reason/reviewer are kept for context.
     */
    public StockVarianceResponseDTO resubmit(Long id, StockVarianceCreateDTO dto) {
        StockVariance variance = getEntity(id);
        Long currentUserId = auth.getCurrentUserId();

        if (variance.getVarianceStatus() != StockVarianceStatus.SENT_BACK) {
            throw new IllegalArgumentException("Only sent-back variances can be resubmitted");
        }
        if (!variance.getCreatedBy().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Only the original requester can resubmit this variance");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }

        Long companyId = auth.getCurrentCompanyId();
        ComputedVariance c = computeVarianceFields(dto, companyId);

        variance.setItem(c.item);
        variance.setFromWarehouse(c.fromWarehouse);
        variance.setToWarehouse(c.toWarehouse);
        variance.setVarianceType(c.varianceType);
        variance.setAdjustmentMode(c.adjustmentMode);
        variance.setQuantityBefore(c.quantityBefore);
        variance.setQuantityAfter(c.quantityAfter);
        variance.setAdjustmentQuantity(c.adjustmentQuantity);
        variance.setTransferQuantity(c.transferQuantity);
        variance.setReason(dto.getReason().trim());
        variance.setNotes(dto.getNotes());
        variance.setVarianceDate(parseDate(dto.getVarianceDate()));
        variance.setVarianceStatus(StockVarianceStatus.PENDING);

        return toDTO(repo.save(variance));
    }

    private static class ComputedVariance {
        Item item;
        Warehouse fromWarehouse;
        Warehouse toWarehouse;
        String varianceType;
        String adjustmentMode;
        int quantityBefore;
        Integer quantityAfter;
        Integer adjustmentQuantity;
        Integer transferQuantity;
    }

    private ComputedVariance computeVarianceFields(StockVarianceCreateDTO dto, Long companyId) {
        if (dto.getItemId() == null || dto.getWarehouseId() == null) {
            throw new IllegalArgumentException("Item and warehouse are required");
        }

        ComputedVariance c = new ComputedVariance();
        c.item = loadItem(dto.getItemId(), companyId);
        c.fromWarehouse = loadWarehouse(dto.getWarehouseId(), companyId);
        c.varianceType = normalizeType(dto.getVarianceType());
        c.adjustmentMode = normalizeMode(dto.getAdjustmentMode(), c.varianceType);

        c.quantityBefore = stockService.getQuantityOnHand(c.item.getId(), c.fromWarehouse.getId(), companyId);

        if ("transfer".equals(c.varianceType)) {
            if (dto.getToWarehouseId() == null) {
                throw new IllegalArgumentException("Destination warehouse is required for transfer");
            }
            c.transferQuantity = requirePositive(dto.getTransferQuantity(), "Transfer quantity");
            c.toWarehouse = loadWarehouse(dto.getToWarehouseId(), companyId);
            if (c.fromWarehouse.getId().equals(c.toWarehouse.getId())) {
                throw new IllegalArgumentException("Source and destination warehouse must differ");
            }
            if (c.transferQuantity > c.quantityBefore) {
                throw new IllegalArgumentException("Transfer quantity exceeds on-hand stock");
            }
            c.quantityAfter = c.quantityBefore - c.transferQuantity;
            c.adjustmentMode = "transfer";
        } else if ("set".equals(c.adjustmentMode)) {
            c.quantityAfter = requireNonNegative(dto.getNewQuantity(), "New quantity");
            c.adjustmentQuantity = c.quantityAfter - c.quantityBefore;
        } else {
            c.adjustmentQuantity = dto.getAdjustmentQuantity();
            if (c.adjustmentQuantity == null || c.adjustmentQuantity == 0) {
                throw new IllegalArgumentException("Adjustment quantity cannot be zero");
            }
            c.quantityAfter = c.quantityBefore + c.adjustmentQuantity;
            if (c.quantityAfter < 0) {
                throw new IllegalArgumentException("Resulting quantity cannot be negative");
            }
        }

        return c;
    }

    public StockVarianceResponseDTO approve(Long id) {
        StockVariance variance = getEntity(id);
        User approver = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (variance.getVarianceStatus() != StockVarianceStatus.PENDING) {
            throw new IllegalArgumentException("Only pending variances can be approved");
        }
        if (!canApprove(approver, variance)) {
            throw new AccessDeniedException(
                    "Only warehouse manager, finance manager, or CEO can approve variances");
        }

        Long companyId = auth.getCurrentCompanyId();
        BigDecimal fifoAmount = applyStockChange(variance, companyId);
        postFinanceIfNeeded(variance, companyId, fifoAmount);

        variance.setVarianceStatus(StockVarianceStatus.APPROVED);
        variance.setApprovedBy(approver);
        variance.setApprovedAt(Instant.now());
        return toDTO(repo.save(variance));
    }

    public StockVarianceResponseDTO reject(Long id) {
        StockVariance variance = getEntity(id);
        User rejecter = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (variance.getVarianceStatus() != StockVarianceStatus.PENDING) {
            throw new IllegalArgumentException("Only pending variances can be rejected");
        }
        if (!canApprove(rejecter, variance)) {
            throw new AccessDeniedException(
                    "Only warehouse manager, finance manager, or CEO can reject variances");
        }

        variance.setVarianceStatus(StockVarianceStatus.REJECTED);
        variance.setRejectedBy(rejecter);
        variance.setRejectedAt(Instant.now());
        return toDTO(repo.save(variance));
    }

    /**
     * Returns a pending variance to its requester with a reason, instead of a terminal
     * rejection. The requester sees it under "sent back to you" and can revise and
     * resubmit it via {@link #resubmit}.
     */
    public StockVarianceResponseDTO sendBack(Long id, String reason) {
        StockVariance variance = getEntity(id);
        User sender = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (variance.getVarianceStatus() != StockVarianceStatus.PENDING) {
            throw new IllegalArgumentException("Only pending variances can be sent back");
        }
        if (!canApprove(sender, variance)) {
            throw new AccessDeniedException(
                    "Only warehouse manager, finance manager, or CEO can send back variances");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to send this variance back");
        }

        variance.setVarianceStatus(StockVarianceStatus.SENT_BACK);
        variance.setSentBackBy(sender);
        variance.setSentBackAt(Instant.now());
        variance.setSentBackReason(reason.trim());
        return toDTO(repo.save(variance));
    }

    @Transactional(readOnly = true)
    public List<StockVarianceResponseDTO> listPending() {
        return repo.findByCompanyIdAndVarianceStatusOrderByCreatedAtDesc(
                        auth.getCurrentCompanyId(), StockVarianceStatus.PENDING)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockVarianceResponseDTO> listSentBackToMe() {
        return repo.findByCompanyIdAndVarianceStatusAndCreatedBy_IdOrderByCreatedAtDesc(
                        auth.getCurrentCompanyId(), StockVarianceStatus.SENT_BACK, auth.getCurrentUserId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockVarianceResponseDTO> listHistory(Boolean archived) {
        boolean showArchived = Boolean.TRUE.equals(archived);
        return repo.findByCompanyIdAndVarianceStatusInAndArchivedOrderByCreatedAtDesc(
                        auth.getCurrentCompanyId(),
                        List.of(StockVarianceStatus.APPROVED, StockVarianceStatus.REJECTED),
                        showArchived)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public StockVarianceResponseDTO archive(Long id) {
        StockVariance variance = getEntity(id);
        if (variance.isArchived()) {
            return toDTO(variance);
        }
        if (variance.getVarianceStatus() != StockVarianceStatus.APPROVED
                && variance.getVarianceStatus() != StockVarianceStatus.REJECTED) {
            throw new IllegalArgumentException("Only approved or rejected variances can be archived");
        }
        variance.setArchived(true);
        return toDTO(repo.save(variance));
    }

    @Transactional(readOnly = true)
    public boolean canCurrentUserApprove() {
        if (hasInventoryStockApprovePermission()) {
            return true;
        }
        User user = userRepo.findById(auth.getCurrentUserId()).orElse(null);
        return user != null && hasElevatedApprovalRole(user);
    }

    private BigDecimal applyStockChange(StockVariance variance, Long companyId) {
        Long itemId = variance.getItem().getId();
        Long fromWhId = variance.getFromWarehouse().getId();

        if ("transfer".equals(variance.getVarianceType())) {
            stockBatchService.transferFifo(
                    itemId,
                    fromWhId,
                    variance.getToWarehouse().getId(),
                    variance.getTransferQuantity(),
                    variance.getId(),
                    companyId);
            return BigDecimal.ZERO;
        }

        int delta;
        if ("set".equals(variance.getAdjustmentMode())) {
            int current = stockService.getQuantityOnHand(itemId, fromWhId, companyId);
            delta = variance.getQuantityAfter() - current;
        } else {
            delta = variance.getAdjustmentQuantity() != null ? variance.getAdjustmentQuantity() : 0;
        }

        if (delta == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal unitCost = variance.getItem().getCostPrice();
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0) {
            unitCost = variance.getItem().getSellingPrice();
        }
        if (unitCost == null) {
            unitCost = BigDecimal.ZERO;
        }

        if (delta < 0) {
            StockBatchService.ConsumptionResult consumed = stockBatchService.consumeFifoForVariance(
                    itemId, fromWhId, Math.abs(delta), variance.getId(), companyId);
            return consumed.totalCost();
        }

        stockBatchService.receiveIntoBatch(
                itemId,
                fromWhId,
                delta,
                unitCost,
                null,
                null,
                com.erp.domain.inventory.StockBatchSourceType.ADJUSTMENT,
                variance.getId(),
                companyId
        );
        return unitCost.multiply(BigDecimal.valueOf(delta));
    }

    private void postFinanceIfNeeded(StockVariance variance, Long companyId, BigDecimal fifoAmount) {
        if ("transfer".equals(variance.getVarianceType())) {
            return;
        }

        int qtyChange = variance.getAdjustmentQuantity() != null ? variance.getAdjustmentQuantity() : 0;
        if ("set".equals(variance.getAdjustmentMode())) {
            qtyChange = (variance.getQuantityAfter() != null ? variance.getQuantityAfter() : 0)
                    - (variance.getQuantityBefore() != null ? variance.getQuantityBefore() : 0);
        }
        if (qtyChange == 0) {
            return;
        }

        BigDecimal amount = fifoAmount != null && fifoAmount.compareTo(BigDecimal.ZERO) > 0
                ? fifoAmount
                : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal unitCost = variance.getItem().getCostPrice();
            if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                unitCost = variance.getItem().getSellingPrice();
            }
            if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            amount = unitCost.multiply(BigDecimal.valueOf(Math.abs(qtyChange)));
        }
        ProcessAccountPair varianceAccounts = accountingDefaults.requireProcessAccounts(
                companyId, AccountingProcessCode.STOCK_VARIANCE);
        Long inventoryAccountId = varianceAccounts.getDebitAccountId();
        Long expenseAccountId = varianceAccounts.getCreditAccountId();
        accountingDefaults.assertDistinctAccounts(
                "Stock variance posting", inventoryAccountId, expenseAccountId);

        boolean inventoryIncrease = qtyChange > 0;
        Transaction tx = transactionService.createForStockVariance(
                companyId,
                variance.getId(),
                amount,
                inventoryAccountId,
                expenseAccountId,
                inventoryIncrease,
                variance.getVarianceDate(),
                "Stock variance " + variance.getVarianceType() + " — " + variance.getItem().getSku());

        variance.setFinanceTransactionId(tx.getId());
    }

    private boolean canApprove(User user, StockVariance variance) {
        if (hasInventoryStockApprovePermission() || hasElevatedApprovalRole(user)) {
            return true;
        }
        Long userId = user.getId();
        Warehouse from = variance.getFromWarehouse();
        if (from.getManager() != null && userId.equals(from.getManager().getId())) {
            return true;
        }
        Warehouse to = variance.getToWarehouse();
        return to != null
                && to.getManager() != null
                && userId.equals(to.getManager().getId());
    }

    private boolean hasInventoryStockApprovePermission() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && permissionCheckService.hasAccess(auth, AppModule.INVENTORY_STOCK, AppAction.APPROVE);
    }

    private boolean hasElevatedApprovalRole(User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        String role = user.getCompanyRole();
        if (role == null) {
            return false;
        }
        String normalized = role.toLowerCase(Locale.ROOT);
        return normalized.contains("warehouse manager")
                || normalized.contains("finance manager")
                || normalized.equals("ceo")
                || normalized.contains("chief executive");
    }

    private StockVariance getEntity(Long id) {
        StockVariance variance = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Variance not found"));
        if (!variance.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new AccessDeniedException("Variance does not belong to your company");
        }
        return variance;
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
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        if (!warehouse.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Warehouse not found");
        }
        return warehouse;
    }

    private StockVarianceResponseDTO toDTO(StockVariance v) {
        return StockVarianceResponseDTO.builder()
                .id(v.getId())
                .status(v.getVarianceStatus().name().toLowerCase())
                .varianceType(v.getVarianceType())
                .adjustmentMode(v.getAdjustmentMode())
                .itemId(v.getItem().getId())
                .itemName(v.getItem().getName())
                .itemSku(v.getItem().getSku())
                .fromWarehouseId(v.getFromWarehouse().getId())
                .fromWarehouseName(v.getFromWarehouse().getName())
                .toWarehouseId(v.getToWarehouse() != null ? v.getToWarehouse().getId() : null)
                .toWarehouseName(v.getToWarehouse() != null ? v.getToWarehouse().getName() : null)
                .quantityBefore(v.getQuantityBefore())
                .quantityAfter(v.getQuantityAfter())
                .adjustmentQuantity(v.getAdjustmentQuantity())
                .transferQuantity(v.getTransferQuantity())
                .reason(v.getReason())
                .notes(v.getNotes())
                .varianceDate(v.getVarianceDate())
                .financeTransactionId(v.getFinanceTransactionId())
                .createdById(v.getCreatedBy().getId())
                .createdByName(v.getCreatedBy().getFullName())
                .createdAt(v.getCreatedAt())
                .approvedById(v.getApprovedBy() != null ? v.getApprovedBy().getId() : null)
                .approvedByName(v.getApprovedBy() != null ? v.getApprovedBy().getFullName() : null)
                .approvedAt(v.getApprovedAt())
                .rejectedById(v.getRejectedBy() != null ? v.getRejectedBy().getId() : null)
                .rejectedByName(v.getRejectedBy() != null ? v.getRejectedBy().getFullName() : null)
                .rejectedAt(v.getRejectedAt())
                .sentBackById(v.getSentBackBy() != null ? v.getSentBackBy().getId() : null)
                .sentBackByName(v.getSentBackBy() != null ? v.getSentBackBy().getFullName() : null)
                .sentBackAt(v.getSentBackAt())
                .sentBackReason(v.getSentBackReason())
                .archived(v.isArchived())
                .build();
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Variance type is required");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMode(String mode, String varianceType) {
        if ("transfer".equals(varianceType)) {
            return "transfer";
        }
        if (mode == null || mode.isBlank()) {
            return "delta";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(value);
    }

    private static int requirePositive(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static int requireNonNegative(Integer value, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(label + " must be zero or greater");
        }
        return value;
    }
}
