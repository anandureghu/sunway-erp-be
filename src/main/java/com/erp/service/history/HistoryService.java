package com.erp.service.history;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.finance.Transaction;
import com.erp.domain.history.HistoryEntityType;
import com.erp.domain.history.HistoryModule;
import com.erp.domain.inventory.StockVariance;
import com.erp.domain.purchase.GoodsReceipt;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.domain.sales.SalesOrder;
import com.erp.dto.history.BulkActionFailureDTO;
import com.erp.dto.history.BulkActionResultDTO;
import com.erp.dto.history.HistoryPageResponse;
import com.erp.dto.history.HistoryRecordDTO;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.JournalEntryRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.inventory.StockVarianceRepository;
import com.erp.repo.purchase.GoodsReceiptRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.repo.sales.PicklistRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.InvoiceService;
import com.erp.service.finance.JournalEntryService;
import com.erp.service.finance.PaymentService;
import com.erp.service.finance.TransactionService;
import com.erp.service.inventory.StockVarianceService;
import com.erp.service.purchase.GoodsReceiptService;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.purchase.PurchaseRequisitionService;
import com.erp.service.sales.SalesOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

@Service
@Transactional
public class HistoryService {

    private static final String DELETE_TOKEN = "DELETE";

    private final AuthContext auth;
    private final SalesOrderRepository salesOrderRepo;
    private final PicklistRepository picklistRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PurchaseRequisitionRepository purchaseRequisitionRepo;
    private final StockVarianceRepository stockVarianceRepo;
    private final GoodsReceiptRepository goodsReceiptRepo;
    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;
    private final JournalEntryRepository journalEntryRepo;
    private final TransactionRepository transactionRepo;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseRequisitionService purchaseRequisitionService;
    private final StockVarianceService stockVarianceService;
    private final GoodsReceiptService goodsReceiptService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final JournalEntryService journalEntryService;
    private final TransactionService transactionService;

    public HistoryService(
            AuthContext auth,
            SalesOrderRepository salesOrderRepo,
            PicklistRepository picklistRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            PurchaseRequisitionRepository purchaseRequisitionRepo,
            StockVarianceRepository stockVarianceRepo,
            GoodsReceiptRepository goodsReceiptRepo,
            InvoiceRepository invoiceRepo,
            PaymentRepository paymentRepo,
            JournalEntryRepository journalEntryRepo,
            TransactionRepository transactionRepo,
            SalesOrderService salesOrderService,
            PurchaseOrderService purchaseOrderService,
            PurchaseRequisitionService purchaseRequisitionService,
            StockVarianceService stockVarianceService,
            GoodsReceiptService goodsReceiptService,
            InvoiceService invoiceService,
            PaymentService paymentService,
            JournalEntryService journalEntryService,
            TransactionService transactionService
    ) {
        this.auth = auth;
        this.salesOrderRepo = salesOrderRepo;
        this.picklistRepo = picklistRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.purchaseRequisitionRepo = purchaseRequisitionRepo;
        this.stockVarianceRepo = stockVarianceRepo;
        this.goodsReceiptRepo = goodsReceiptRepo;
        this.invoiceRepo = invoiceRepo;
        this.paymentRepo = paymentRepo;
        this.journalEntryRepo = journalEntryRepo;
        this.transactionRepo = transactionRepo;
        this.salesOrderService = salesOrderService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseRequisitionService = purchaseRequisitionService;
        this.stockVarianceService = stockVarianceService;
        this.goodsReceiptService = goodsReceiptService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.journalEntryService = journalEntryService;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public HistoryPageResponse list(
            HistoryModule module,
            HistoryEntityType type,
            int page,
            int size,
            String search
    ) {
        validateTypeForModule(module, type);
        Long companyId = requireCompanyId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String normalizedSearch = normalizeSearch(search);

        Page<HistoryRecordDTO> result = switch (type) {
            case SALES_ORDER -> mapPage(
                    salesOrderRepo.findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(companyId, pageable),
                    this::toSalesOrderRecord,
                    normalizedSearch
            );
            case PURCHASE_ORDER -> mapPage(
                    purchaseOrderRepo.findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(companyId, pageable),
                    this::toPurchaseOrderRecord,
                    normalizedSearch
            );
            case PURCHASE_REQUISITION -> mapPage(
                    purchaseRequisitionRepo.findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(companyId, pageable),
                    this::toPurchaseRequisitionRecord,
                    normalizedSearch
            );
            case STOCK_VARIANCE -> mapPage(
                    stockVarianceRepo.findByCompanyIdAndArchivedTrueOrderByCreatedAtDesc(companyId, pageable),
                    this::toStockVarianceRecord,
                    normalizedSearch
            );
            case GOODS_RECEIPT -> mapPage(
                    goodsReceiptRepo.findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(companyId, pageable),
                    this::toGoodsReceiptRecord,
                    normalizedSearch
            );
            case SALES_INVOICE -> mapPage(
                    invoiceRepo.findByCompany_IdAndArchivedTrueAndTypeOrderByCreatedAtDesc(
                            companyId, InvoiceType.SALES, pageable),
                    this::toInvoiceRecord,
                    normalizedSearch
            );
            case PURCHASE_INVOICE -> mapPage(
                    invoiceRepo.findByCompany_IdAndArchivedTrueAndTypeOrderByCreatedAtDesc(
                            companyId, InvoiceType.PURCHASE, pageable),
                    this::toInvoiceRecord,
                    normalizedSearch
            );
            case CUSTOMER_PAYMENT -> mapPage(
                    paymentRepo.findByCompany_IdAndArchivedTrueAndPaymentDirectionOrderByCreatedAtDesc(
                            companyId, PaymentDirection.CUSTOMER, pageable),
                    this::toPaymentRecord,
                    normalizedSearch
            );
            case VENDOR_PAYMENT -> mapPage(
                    paymentRepo.findByCompany_IdAndArchivedTrueAndPaymentDirectionOrderByCreatedAtDesc(
                            companyId, PaymentDirection.VENDOR, pageable),
                    this::toPaymentRecord,
                    normalizedSearch
            );
            case JOURNAL_ENTRY -> mapPage(
                    journalEntryRepo.findAllByCompanyIdAndArchived(companyId, true, pageable),
                    this::toJournalEntryRecord,
                    normalizedSearch
            );
            case TRANSACTION -> mapPage(
                    transactionRepo.findByCompany_IdAndArchivedTrueAndTransactionTypeNotOrderByCreatedAtDesc(
                            companyId, TransactionService.TYPE_BUDGET_DISTRIBUTION, pageable),
                    this::toTransactionRecord,
                    normalizedSearch
            );
            case BUDGET_DISTRIBUTION -> mapPage(
                    transactionRepo.findByCompany_IdAndArchivedTrueAndTransactionTypeOrderByCreatedAtDesc(
                            companyId, TransactionService.TYPE_BUDGET_DISTRIBUTION, pageable),
                    this::toTransactionRecord,
                    normalizedSearch
            );
        };

        return HistoryPageResponse.builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public BulkActionResultDTO bulkArchive(HistoryEntityType type, List<Long> ids) {
        return runBulk(type, ids, id -> archiveOne(type, id));
    }

    public BulkActionResultDTO bulkDelete(HistoryEntityType type, List<Long> ids) {
        return runBulk(type, ids, id -> deleteOne(type, id));
    }

    public BulkActionResultDTO deleteAll(HistoryEntityType type, String confirmToken) {
        if (!DELETE_TOKEN.equals(confirmToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type DELETE to confirm permanent deletion");
        }
        Long companyId = requireCompanyId();
        List<Long> ids = listAllArchivedIds(type, companyId);
        return runBulk(type, ids, id -> deleteOne(type, id));
    }

    private BulkActionResultDTO runBulk(
            HistoryEntityType type,
            List<Long> ids,
            ThrowingConsumer<Long> action
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No records selected");
        }
        BulkActionResultDTO result = BulkActionResultDTO.builder().build();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            try {
                action.accept(id);
                result.getSucceeded().add(id);
            } catch (Exception ex) {
                result.getFailed().add(BulkActionFailureDTO.builder()
                        .id(id)
                        .reason(ex.getMessage() != null ? ex.getMessage() : "Operation failed")
                        .build());
            }
        }
        return result;
    }

    private void archiveOne(HistoryEntityType type, Long id) {
        switch (type) {
            case SALES_ORDER -> salesOrderService.archive(id);
            case PURCHASE_ORDER -> purchaseOrderService.archive(id);
            case PURCHASE_REQUISITION -> purchaseRequisitionService.archive(id);
            case STOCK_VARIANCE -> stockVarianceService.archive(id);
            case GOODS_RECEIPT -> goodsReceiptService.archive(id);
            case SALES_INVOICE, PURCHASE_INVOICE -> invoiceService.archiveInvoice(id);
            case CUSTOMER_PAYMENT, VENDOR_PAYMENT -> paymentService.archivePayment(id);
            case JOURNAL_ENTRY -> journalEntryService.archive(id);
            case TRANSACTION, BUDGET_DISTRIBUTION -> transactionService.archiveTransaction(id);
        }
    }

    private void deleteOne(HistoryEntityType type, Long id) {
        Long companyId = requireCompanyId();
        switch (type) {
            case SALES_ORDER -> deleteSalesOrder(id, companyId);
            case PURCHASE_ORDER -> deletePurchaseOrder(id, companyId);
            case PURCHASE_REQUISITION -> deletePurchaseRequisition(id, companyId);
            case STOCK_VARIANCE -> deleteStockVariance(id, companyId);
            case GOODS_RECEIPT -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Goods receipts cannot be permanently deleted");
            case SALES_INVOICE -> deleteInvoice(id, companyId, InvoiceType.SALES);
            case PURCHASE_INVOICE -> deleteInvoice(id, companyId, InvoiceType.PURCHASE);
            case CUSTOMER_PAYMENT -> deletePayment(id, companyId, PaymentDirection.CUSTOMER);
            case VENDOR_PAYMENT -> deletePayment(id, companyId, PaymentDirection.VENDOR);
            case JOURNAL_ENTRY -> deleteJournalEntry(id, companyId);
            case TRANSACTION, BUDGET_DISTRIBUTION -> deleteTransaction(id, companyId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported type");
        }
    }

    private void deleteSalesOrder(Long id, Long companyId) {
        SalesOrder order = salesOrderRepo.findById(id)
                .filter(o -> o.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Sales order not found"));
        if (!order.isArchived()) {
            throw new RuntimeException("Only archived sales orders can be permanently deleted");
        }
        if (picklistRepo.findBySalesOrderId(id).isPresent()) {
            throw new RuntimeException("Cannot delete: picklist exists for this order");
        }
        if (invoiceRepo.findByOrderIdAndType(id, InvoiceType.SALES).isPresent()) {
            throw new RuntimeException("Cannot delete: invoice exists for this order");
        }
        salesOrderRepo.delete(order);
    }

    private void deletePurchaseOrder(Long id, Long companyId) {
        PurchaseOrder po = purchaseOrderRepo.findById(id)
                .filter(p -> p.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
        if (!po.isArchived()) {
            throw new RuntimeException("Only archived purchase orders can be permanently deleted");
        }
        if (invoiceRepo.findByOrderIdAndType(id, InvoiceType.PURCHASE).isPresent()) {
            throw new RuntimeException("Cannot delete: invoice exists for this purchase order");
        }
        purchaseOrderRepo.delete(po);
    }

    private void deletePurchaseRequisition(Long id, Long companyId) {
        PurchaseRequisition pr = purchaseRequisitionRepo.findById(id)
                .filter(p -> p.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Purchase requisition not found"));
        if (!pr.isArchived()) {
            throw new RuntimeException("Only archived requisitions can be permanently deleted");
        }
        if (purchaseOrderRepo.findBySourceRequisition_Id(id).isPresent()) {
            throw new RuntimeException("Cannot delete: purchase order exists for this requisition");
        }
        purchaseRequisitionRepo.delete(pr);
    }

    private void deleteStockVariance(Long id, Long companyId) {
        StockVariance variance = stockVarianceRepo.findById(id)
                .filter(v -> v.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Stock variance not found"));
        if (!variance.isArchived()) {
            throw new RuntimeException("Only archived variances can be permanently deleted");
        }
        stockVarianceRepo.delete(variance);
    }

    private void deleteInvoice(Long id, Long companyId, InvoiceType type) {
        Invoice invoice = invoiceRepo.findById(id)
                .filter(i -> i.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!invoice.isArchived()) {
            throw new RuntimeException("Only archived invoices can be permanently deleted");
        }
        if (invoice.getType() != type) {
            throw new RuntimeException("Invoice type mismatch");
        }
        invoiceService.deleteInvoice(id);
    }

    private void deletePayment(Long id, Long companyId, PaymentDirection direction) {
        Payment payment = paymentRepo.findById(id)
                .filter(p -> p.getCompany() != null && companyId.equals(p.getCompany().getId()))
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (!payment.isArchived()) {
            throw new RuntimeException("Only archived payments can be permanently deleted");
        }
        if (payment.getPaymentDirection() != direction) {
            throw new RuntimeException("Payment direction mismatch");
        }
        paymentService.deletePayment(id);
    }

    private void deleteJournalEntry(Long id, Long companyId) {
        JournalEntry entry = journalEntryRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Journal entry not found"));
        if (!Boolean.TRUE.equals(entry.getArchived())) {
            throw new RuntimeException("Only archived journal entries can be permanently deleted");
        }
        journalEntryRepo.delete(entry);
    }

    private void deleteTransaction(Long id, Long companyId) {
        Transaction tx = transactionRepo.findById(id)
                .filter(t -> t.getCompany() != null && companyId.equals(t.getCompany().getId()))
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (!Boolean.TRUE.equals(tx.getArchived())) {
            throw new RuntimeException("Only archived transactions can be permanently deleted");
        }
        transactionRepo.delete(tx);
    }

    private List<Long> listAllArchivedIds(HistoryEntityType type, Long companyId) {
        return switch (type) {
            case SALES_ORDER -> salesOrderRepo.findByCompanyIdAndArchivedTrue(companyId).stream().map(SalesOrder::getId).toList();
            case PURCHASE_ORDER -> purchaseOrderRepo.findByCompanyIdAndArchivedTrue(companyId).stream().map(PurchaseOrder::getId).toList();
            case PURCHASE_REQUISITION -> purchaseRequisitionRepo.findByCompanyIdAndArchivedTrue(companyId).stream().map(PurchaseRequisition::getId).toList();
            case STOCK_VARIANCE -> stockVarianceRepo.findByCompanyIdAndArchivedTrue(companyId).stream().map(StockVariance::getId).toList();
            case GOODS_RECEIPT -> goodsReceiptRepo.findByCompany_IdAndArchivedTrueOrderByReceivedAtDesc(companyId).stream().map(GoodsReceipt::getId).toList();
            case SALES_INVOICE -> invoiceRepo.findByCompany_IdAndArchivedTrueAndType(companyId, InvoiceType.SALES).stream().map(Invoice::getId).toList();
            case PURCHASE_INVOICE -> invoiceRepo.findByCompany_IdAndArchivedTrueAndType(companyId, InvoiceType.PURCHASE).stream().map(Invoice::getId).toList();
            case CUSTOMER_PAYMENT -> paymentRepo.findByCompany_IdAndArchivedTrueAndPaymentDirection(companyId, PaymentDirection.CUSTOMER).stream().map(Payment::getId).toList();
            case VENDOR_PAYMENT -> paymentRepo.findByCompany_IdAndArchivedTrueAndPaymentDirection(companyId, PaymentDirection.VENDOR).stream().map(Payment::getId).toList();
            case JOURNAL_ENTRY -> journalEntryRepo.findAllByCompanyIdAndArchivedTrue(companyId).stream().map(JournalEntry::getId).toList();
            case TRANSACTION -> transactionRepo.findByCompany_IdAndArchivedTrueAndTransactionTypeNot(
                    companyId, TransactionService.TYPE_BUDGET_DISTRIBUTION).stream().map(Transaction::getId).toList();
            case BUDGET_DISTRIBUTION -> transactionRepo.findByCompany_IdAndArchivedTrueAndTransactionType(
                    companyId, TransactionService.TYPE_BUDGET_DISTRIBUTION).stream().map(Transaction::getId).toList();
        };
    }

    private void validateTypeForModule(HistoryModule module, HistoryEntityType type) {
        if (type == null || module == null || type.getModule() != module) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history type for module");
        }
    }

    private Long requireCompanyId() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company is required");
        }
        return companyId;
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private <T> Page<HistoryRecordDTO> mapPage(
            Page<T> page,
            Function<T, HistoryRecordDTO> mapper,
            String search
    ) {
        if (search == null) {
            return page.map(mapper);
        }
        List<HistoryRecordDTO> filtered = new ArrayList<>();
        for (T item : page.getContent()) {
            HistoryRecordDTO dto = mapper.apply(item);
            if (matchesSearch(dto, search)) {
                filtered.add(dto);
            }
        }
        return new org.springframework.data.domain.PageImpl<>(filtered, page.getPageable(), page.getTotalElements());
    }

    private boolean matchesSearch(HistoryRecordDTO dto, String search) {
        return contains(dto.getReferenceNo(), search)
                || contains(dto.getPartyName(), search)
                || contains(dto.getStatus(), search);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private HistoryRecordDTO toSalesOrderRecord(SalesOrder order) {
        return HistoryRecordDTO.builder()
                .id(order.getId())
                .type(HistoryEntityType.SALES_ORDER)
                .referenceNo(order.getOrderNumber())
                .status(order.getStatus())
                .partyName(order.getCustomer() != null ? order.getCustomer().getCustomerName() : null)
                .amount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toPurchaseOrderRecord(PurchaseOrder po) {
        return HistoryRecordDTO.builder()
                .id(po.getId())
                .type(HistoryEntityType.PURCHASE_ORDER)
                .referenceNo(po.getOrderNumber())
                .status(po.getStatus() != null ? po.getStatus().name() : null)
                .partyName(po.getSupplier() != null ? po.getSupplier().getVendorName() : null)
                .amount(po.getTotalAmount())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toPurchaseRequisitionRecord(PurchaseRequisition pr) {
        return HistoryRecordDTO.builder()
                .id(pr.getId())
                .type(HistoryEntityType.PURCHASE_REQUISITION)
                .referenceNo(pr.getRequisitionNumber())
                .status(pr.getStatus() != null ? pr.getStatus().name() : null)
                .partyName(pr.getRequestedBy() != null ? pr.getRequestedBy().getFullName() : null)
                .createdAt(pr.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toStockVarianceRecord(StockVariance variance) {
        return HistoryRecordDTO.builder()
                .id(variance.getId())
                .type(HistoryEntityType.STOCK_VARIANCE)
                .referenceNo("VAR-" + variance.getId())
                .status(variance.getVarianceStatus() != null ? variance.getVarianceStatus().name() : null)
                .partyName(variance.getItem() != null ? variance.getItem().getName() : null)
                .createdAt(variance.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toGoodsReceiptRecord(GoodsReceipt gr) {
        return HistoryRecordDTO.builder()
                .id(gr.getId())
                .type(HistoryEntityType.GOODS_RECEIPT)
                .referenceNo("GRN-" + gr.getId())
                .status(gr.getStatus() != null ? gr.getStatus().name() : null)
                .partyName(gr.getPurchaseOrder() != null && gr.getPurchaseOrder().getSupplier() != null
                        ? gr.getPurchaseOrder().getSupplier().getVendorName()
                        : null)
                .createdAt(gr.getReceivedAt())
                .build();
    }

    private HistoryRecordDTO toInvoiceRecord(Invoice invoice) {
        HistoryEntityType type = invoice.getType() == InvoiceType.PURCHASE
                ? HistoryEntityType.PURCHASE_INVOICE
                : HistoryEntityType.SALES_INVOICE;
        return HistoryRecordDTO.builder()
                .id(invoice.getId())
                .type(type)
                .referenceNo(invoice.getInvoiceId())
                .status(invoice.getStatus())
                .partyName(invoice.getToParty())
                .amount(invoice.getAmount())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toPaymentRecord(Payment payment) {
        HistoryEntityType type = payment.getPaymentDirection() == PaymentDirection.VENDOR
                ? HistoryEntityType.VENDOR_PAYMENT
                : HistoryEntityType.CUSTOMER_PAYMENT;
        return HistoryRecordDTO.builder()
                .id(payment.getId())
                .type(type)
                .referenceNo(payment.getPaymentCode())
                .status(payment.getPaymentMethod())
                .partyName(payment.getInvoiceId())
                .amount(payment.getAmount())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private HistoryRecordDTO toJournalEntryRecord(JournalEntry entry) {
        return HistoryRecordDTO.builder()
                .id(entry.getId())
                .type(HistoryEntityType.JOURNAL_ENTRY)
                .referenceNo(entry.getJeNumber())
                .status(entry.getStatus() != null ? entry.getStatus().name() : null)
                .partyName(entry.getDescription())
                .amount(entry.getAmount())
                .createdAt(entry.getCreatedAt() != null ? entry.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant() : null)
                .archivedAt(entry.getArchivedAt() != null ? entry.getArchivedAt().atZone(java.time.ZoneOffset.UTC).toInstant() : null)
                .build();
    }

    private HistoryRecordDTO toTransactionRecord(Transaction tx) {
        HistoryEntityType type = Objects.equals(tx.getTransactionType(), TransactionService.TYPE_BUDGET_DISTRIBUTION)
                ? HistoryEntityType.BUDGET_DISTRIBUTION
                : HistoryEntityType.TRANSACTION;
        return HistoryRecordDTO.builder()
                .id(tx.getId())
                .type(type)
                .referenceNo(tx.getTransactionCode())
                .status(tx.getTransactionType())
                .partyName(tx.getTransactionDescription())
                .amount(tx.getAmount())
                .createdAt(tx.getCreatedAt())
                .archivedAt(tx.getArchivedAt())
                .build();
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value);
    }
}
