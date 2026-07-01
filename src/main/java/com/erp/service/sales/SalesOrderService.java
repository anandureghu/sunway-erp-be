package com.erp.service.sales;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.InvoiceType;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.sales.SalesOrderItem;
import com.erp.dto.sales.SalesOrderCreateDTO;
import com.erp.dto.sales.SalesOrderItemResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.dto.sales.SalesOrderUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CustomerRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.CoaBalanceRules;
import com.erp.service.inventory.ItemWarehouseStockService;
import com.erp.service.inventory.StockBatchService;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional
public class SalesOrderService {

    private final SalesOrderRepository repo;
    private final CustomerRepository customerRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final StockBatchService stockBatchService;
    private final CompanyRepository companyRepo;
    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final InvoiceRepository invoiceRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public SalesOrderService(
            SalesOrderRepository repo,
            CustomerRepository customerRepo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo,
            ItemWarehouseStockService itemWarehouseStockService,
            StockBatchService stockBatchService,
            CompanyRepository companyRepo,
            BankAccountRepository bankAccountRepo,
            ChartOfAccountsRepository coaRepo,
            InvoiceRepository invoiceRepo,
            UserRepository userRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService
    ) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.stockBatchService = stockBatchService;
        this.companyRepo = companyRepo;
        this.bankAccountRepo = bankAccountRepo;
        this.coaRepo = coaRepo;
        this.invoiceRepo = invoiceRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    // --------------------------
    // Create Sales Order (DRAFT)
    // --------------------------
    public SalesOrderResponseDTO create(SalesOrderCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Customer customer = customerRepo.findById(dto.getCustomerId())
                .filter(c -> c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Company company = companyRepo.findById(companyId).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        BankAccount bankAccount = bankAccountRepo.findById(dto.getBankAccountId())
                .filter(b -> b.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
        ChartOfAccounts debitAccount = coaRepo.findById(dto.getDebitAccountId())
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Debit account not found"));
        ChartOfAccounts creditAccount = coaRepo.findById(dto.getCreditAccountId())
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal effectiveTaxRate = resolveCompanyTaxRate(company);

        List<SalesOrderItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            Warehouse lineWh = resolveLineWarehouse(i.getWarehouseId(), companyId);
            itemWarehouseStockService.assertAvailableForSale(
                    item.getId(), lineWh.getId(), i.getQuantity(), companyId);

            BigDecimal qty = BigDecimal.valueOf(i.getQuantity());
            BigDecimal unitPrice = BigDecimal.valueOf(i.getUnitPrice());
            BigDecimal gross = unitPrice.multiply(qty);
            BigDecimal discountPercent = i.getDiscountPercent() == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(i.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new RuntimeException("Discount percent must be between 0 and 100");
            }
            BigDecimal discountAmount = gross.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineSubtotal = gross.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = lineSubtotal.multiply(effectiveTaxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

            return SalesOrderItem.builder()
                    .item(item)
                    .warehouse(lineWh)
                    .quantity(i.getQuantity())
                    .unitPrice(unitPrice)
                    .lineSubtotal(lineSubtotal)
                    .discountPercent(discountPercent)
                    .taxRate(effectiveTaxRate)
                    .taxAmount(taxAmount)
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (SalesOrderItem li : items) {
            subtotal = subtotal.add(li.getLineSubtotal() == null ? BigDecimal.ZERO : li.getLineSubtotal());
            BigDecimal gross = li.getUnitPrice().multiply(BigDecimal.valueOf(li.getQuantity()));
            discountTotal = discountTotal.add(gross.subtract(li.getLineSubtotal() == null ? BigDecimal.ZERO : li.getLineSubtotal()));
            taxTotal = taxTotal.add(li.getTaxAmount() == null ? BigDecimal.ZERO : li.getTaxAmount());
            total = total.add(li.getLineTotal());
        }

        SalesOrder order = SalesOrder.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .orderDate(dto.getOrderDate())
                .invoiceDueDate(dto.getInvoiceDueDate())
                .shippingAddress(dto.getShippingAddress())
                .status("DRAFT")
                .subtotalAmount(subtotal.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(discountTotal.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(taxTotal.setScale(2, RoundingMode.HALF_UP))
                .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
                .company(company)
                .bankAccount(bankAccount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .createdByUser(user)
                .items(items)
                .build();

        return toDTO(repo.save(order));
    }

    // --------------------------
    // Confirm Order
    // --------------------------
    public SalesOrderResponseDTO confirm(Long id) {

        SalesOrder order = getEntity(id);

        if (!"DRAFT".equals(order.getStatus())) {
            throw new RuntimeException("Only DRAFT orders can be confirmed");
        }

        ChartOfAccounts debitAccount = order.getDebitAccount();
        if (debitAccount != null) {
            debitAccount = coaRepo.findById(debitAccount.getId())
                    .orElseThrow(() -> new RuntimeException("Debit account not found"));
            validateSufficientBalanceForSalesOrder(debitAccount, order.getTotalAmount());
        }

        order.setStatus("CONFIRMED");
        Long companyId = auth.getCurrentCompanyId();
        SalesOrder saved = repo.save(order);
        saved.getItems().forEach(i -> {
            Warehouse wh = i.getWarehouse() != null ? i.getWarehouse() : i.getItem().getWarehouse();
            StockBatchService.ConsumptionResult consumption = stockBatchService.consumeFifo(
                    i.getItem().getId(),
                    wh.getId(),
                    i.getQuantity(),
                    StockBatchService.REF_SALES_ORDER_ITEM,
                    i.getId(),
                    companyId
            );
            i.setCogsAmount(consumption.totalCost());
            i.setFifoUnitCost(consumption.weightedUnitCost());
        });

        return toDTO(repo.save(saved));
    }

    // --------------------------
    // Get
    // --------------------------
    public SalesOrderResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    // --------------------------
    // List
    // --------------------------
    public List<SalesOrderResponseDTO> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    // --------------------------
// Update Sales Order (DRAFT only)
// --------------------------
    public SalesOrderResponseDTO update(Long id, SalesOrderUpdateDTO dto) {

        SalesOrder order = getEntity(id);

        if (!"DRAFT".equals(order.getStatus())) {
            throw new RuntimeException("Only DRAFT sales orders can be updated");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new RuntimeException("Sales order must have at least one item");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal effectiveTaxRate = resolveCompanyTaxRate(order.getCompany());

        // Clear existing items (aggregate root rule)
        order.getItems().clear();

        Long companyId = auth.getCurrentCompanyId();

        List<SalesOrderItem> updatedItems = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            Warehouse lineWh = resolveLineWarehouse(i.getWarehouseId(), companyId);
            itemWarehouseStockService.assertAvailableForSale(
                    item.getId(), lineWh.getId(), i.getQuantity(), companyId);

            BigDecimal qty = BigDecimal.valueOf(i.getQuantity());
            BigDecimal unitPrice = BigDecimal.valueOf(i.getUnitPrice());
            BigDecimal gross = unitPrice.multiply(qty);
            BigDecimal discountPercent = i.getDiscountPercent() == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(i.getDiscountPercent());
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new RuntimeException("Discount percent must be between 0 and 100");
            }
            BigDecimal discountAmount = gross.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineSubtotal = gross.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = lineSubtotal.multiply(effectiveTaxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

            return SalesOrderItem.builder()
                    .item(item)
                    .warehouse(lineWh)
                    .quantity(i.getQuantity())
                    .unitPrice(unitPrice)
                    .lineSubtotal(lineSubtotal)
                    .discountPercent(discountPercent)
                    .taxRate(effectiveTaxRate)
                    .taxAmount(taxAmount)
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (SalesOrderItem li : updatedItems) {
            subtotal = subtotal.add(li.getLineSubtotal() == null ? BigDecimal.ZERO : li.getLineSubtotal());
            BigDecimal gross = li.getUnitPrice().multiply(BigDecimal.valueOf(li.getQuantity()));
            discountTotal = discountTotal.add(gross.subtract(li.getLineSubtotal() == null ? BigDecimal.ZERO : li.getLineSubtotal()));
            taxTotal = taxTotal.add(li.getTaxAmount() == null ? BigDecimal.ZERO : li.getTaxAmount());
            total = total.add(li.getLineTotal());
        }

        if (dto.getOrderDate() != null) {
            order.setOrderDate(dto.getOrderDate());
        }
        if (dto.getInvoiceDueDate() != null) {
            order.setInvoiceDueDate(dto.getInvoiceDueDate());
        }
        order.setShippingAddress(dto.getShippingAddress());
        // Keep the same managed collection instance for orphanRemoval/all-delete-orphan.
        order.getItems().clear();
        order.getItems().addAll(updatedItems);
        order.setSubtotalAmount(subtotal.setScale(2, RoundingMode.HALF_UP));
        order.setDiscountAmount(discountTotal.setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxTotal.setScale(2, RoundingMode.HALF_UP));
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

        return toDTO(repo.save(order));
    }

    // --------------------------
// Cancel Sales Order
// --------------------------
    public SalesOrderResponseDTO cancel(Long id) {

        SalesOrder order = getEntity(id);

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Sales order is already cancelled");
        }

        if (!"DRAFT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new RuntimeException("Only DRAFT or CONFIRMED orders can be cancelled");
        }

        if ("CONFIRMED".equals(order.getStatus())) {
            order.getItems().forEach(i ->
                    stockBatchService.restoreByReference(
                            StockBatchService.REF_SALES_ORDER_ITEM,
                            i.getId(),
                            auth.getCurrentCompanyId()
                    )
            );
        }

        order.setStatus("CANCELLED");
        return toDTO(repo.save(order));
    }

    // --------------------------
    // Archive Sales Order
    // --------------------------
    public SalesOrderResponseDTO archive(Long id) {
        SalesOrder order = getEntity(id);
        if (order.isArchived()) {
            return toDTO(order);
        }
        if (!"COMPLETED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Only COMPLETED or CANCELLED orders can be archived");
        }
        order.setArchived(true);
        return toDTO(repo.save(order));
    }


    // --------------------------
    // Helpers
    // --------------------------
    private Warehouse resolveLineWarehouse(Long warehouseId, Long companyId) {
        if (warehouseId == null) {
            throw new RuntimeException("warehouseId is required for each order line");
        }
        return warehouseRepo.findById(warehouseId)
                .filter(w -> w.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
    }

    private Long resolveLineWarehouseId(SalesOrderItem i) {
        if (i.getWarehouse() != null) {
            return i.getWarehouse().getId();
        }
        return i.getItem().getWarehouse().getId();
    }

    private String resolveLineWarehouseName(SalesOrderItem i) {
        if (i.getWarehouse() != null) {
            return i.getWarehouse().getName();
        }
        return i.getItem().getWarehouse().getName();
    }

    private SalesOrder getEntity(Long id) {
        return repo.findById(id)
                .filter(o -> o.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Sales order not found or access denied"));
    }

    private String generateOrderNumber() {
        return documentSequenceService.generateNext("SO");
    }

    private SalesOrderResponseDTO toDTO(SalesOrder so) {
        SalesOrderResponseDTO.SalesOrderResponseDTOBuilder builder = SalesOrderResponseDTO.builder()
                .id(so.getId())
                .orderNumber(so.getOrderNumber())
                .customerId(so.getCustomer().getId())
                .customerName(so.getCustomer().getCustomerName())
                .customerEmail(so.getCustomer().getEmail())
                .customerPhone(so.getCustomer().getPhoneNo())
                .orderDate(so.getOrderDate())
                .invoiceDueDate(so.getInvoiceDueDate())
                .shippingAddress(so.getShippingAddress())
                .status(so.getStatus())
                .archived(so.isArchived())
                .paymentStatus(invoiceRepo.findByOrderIdAndType(so.getId(), InvoiceType.SALES)
                        .map(inv -> inv.getStatus())
                        .orElse("UNPAID"))
                .subtotalAmount(so.getSubtotalAmount() == null ? BigDecimal.ZERO : so.getSubtotalAmount())
                .discountAmount(so.getDiscountAmount() == null ? BigDecimal.ZERO : so.getDiscountAmount())
                .taxAmount(so.getTaxAmount() == null ? BigDecimal.ZERO : so.getTaxAmount())
                .totalAmount(so.getTotalAmount())
                .bankAccountId(so.getBankAccount() != null ? so.getBankAccount().getId() : null)
                .bankAccountName(so.getBankAccount() != null ? so.getBankAccount().getBankName() : null)
                .debitAccountId(so.getDebitAccount() != null ? so.getDebitAccount().getId() : null)
                .debitAccountName(so.getDebitAccount() != null ? so.getDebitAccount().getAccountName() : null)
                .creditAccountId(so.getCreditAccount() != null ? so.getCreditAccount().getId() : null)
                .creditAccountName(so.getCreditAccount() != null ? so.getCreditAccount().getAccountName() : null)
                .salesInvoiceId(
                        invoiceRepo.findByOrderIdAndType(so.getId(), InvoiceType.SALES)
                                .map(Invoice::getId)
                                .orElse(null))
                .items(
                        so.getItems().stream().map(i ->
                                SalesOrderItemResponseDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .itemName(i.getItem().getName())
                                        .quantity(i.getQuantity())
                                        .unitPrice(i.getUnitPrice())
                                        .lineSubtotal(i.getLineSubtotal() == null ? BigDecimal.ZERO : i.getLineSubtotal())
                                        .discountPercent(i.getDiscountPercent() == null ? BigDecimal.ZERO : i.getDiscountPercent())
                                        .taxRate(i.getTaxRate() == null ? BigDecimal.ZERO : i.getTaxRate())
                                        .taxAmount(i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                                        .lineTotal(i.getLineTotal())
                                        .cogsAmount(i.getCogsAmount())
                                        .fifoUnitCost(i.getFifoUnitCost())
                                        .warehouseId(resolveLineWarehouseId(i))
                                        .warehouseName(resolveLineWarehouseName(i))
                                        .build()
                        ).toList()
                );
        applyDebitBalanceInfo(builder, so);
        return builder.build();
    }

    private void applyDebitBalanceInfo(
            SalesOrderResponseDTO.SalesOrderResponseDTOBuilder builder,
            SalesOrder so
    ) {
        if (!"DRAFT".equals(so.getStatus()) || so.getDebitAccount() == null) {
            builder.sufficientDebitBalance(true);
            return;
        }
        ChartOfAccounts debitAccount = so.getDebitAccount();
        BigDecimal current = debitAccount.getBalance() == null ? BigDecimal.ZERO : debitAccount.getBalance();
        BigDecimal total = so.getTotalAmount() == null ? BigDecimal.ZERO : so.getTotalAmount();
        builder.debitAccountBalance(current);
        boolean sufficient = hasSufficientDebitBalance(debitAccount, total);
        builder.sufficientDebitBalance(sufficient);
        if (!sufficient) {
            BigDecimal shortage = total.subtract(current);
            if (shortage.compareTo(BigDecimal.ZERO) < 0) {
                shortage = BigDecimal.ZERO;
            }
            builder.debitBalanceShortage(shortage.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private boolean hasSufficientDebitBalance(ChartOfAccounts debitAccount, BigDecimal totalAmount) {
        if (debitAccount == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        try {
            CoaBalanceRules.assertSufficientBalance(debitAccount, totalAmount.negate());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private BigDecimal resolveCompanyTaxRate(Company company) {
        if (company == null || !company.isTaxActive()) {
            return BigDecimal.ZERO;
        }
        String rawTaxRate = company.getTaxRate();
        if (rawTaxRate == null || rawTaxRate.isBlank()) {
            throw new RuntimeException("Company tax is active but tax rate is not configured");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(rawTaxRate.trim());
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Company tax rate is invalid");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RuntimeException("Company tax rate must be between 0 and 100");
        }
        return rate;
    }

    private void validateSufficientBalanceForSalesOrder(ChartOfAccounts debitAccount, BigDecimal totalAmount) {
        if (debitAccount == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal delta = totalAmount.negate();
        try {
            CoaBalanceRules.assertSufficientBalance(debitAccount, delta);
        } catch (Exception ex) {
            BigDecimal current = debitAccount.getBalance() == null ? BigDecimal.ZERO : debitAccount.getBalance();
            BigDecimal shortage = totalAmount.subtract(current);
            String shortageText = shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage.toPlainString() : "0.00";
            throw new RuntimeException(
                    "Insufficient balance for selected debit account "
                            + debitAccount.getAccountCode()
                            + " ("
                            + debitAccount.getAccountName()
                            + "). Required: "
                            + totalAmount.toPlainString()
                            + ", available: "
                            + current.toPlainString()
                            + ", shortage: "
                            + shortageText);
        }
    }
}
