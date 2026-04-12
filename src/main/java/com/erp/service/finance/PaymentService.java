package com.erp.service.finance;

import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseRequisition;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.notification.CustomerEmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final TransactionService transactionService;
    private final InvoiceService invoiceService;
    private final SalesOrderRepository salesOrderRepo;
    private final CustomerEmailService customerEmailService;
    private final CompanyRepository companyRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final ChartOfAccountsRepository coaRepo;
    private final AuthContext auth;

    public PaymentService(PaymentRepository paymentRepo,
                          TransactionService transactionService,
                          InvoiceService invoiceService,
                          SalesOrderRepository salesOrderRepo,
                          CustomerEmailService customerEmailService,
                          CompanyRepository companyRepo,
                          PurchaseOrderRepository purchaseOrderRepo,
                          ChartOfAccountsRepository coaRepo,
                          AuthContext auth) {

        this.paymentRepo = paymentRepo;
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
        this.salesOrderRepo = salesOrderRepo;
        this.customerEmailService = customerEmailService;
        this.companyRepo = companyRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.coaRepo = coaRepo;
        this.auth = auth;
    }

    @Transactional
    public PaymentResponseDTO createPayment(CreatePaymentDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (dto.getInvoiceId() == null || dto.getInvoiceId().isBlank()) {
            throw new RuntimeException("Invoice ID is required");
        }
        if (dto.getPaymentMethod() == null || dto.getPaymentMethod().isBlank()) {
            throw new RuntimeException("Payment method is required");
        }

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long userId = auth.getCurrentUserId();

//        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
//            throw new RuntimeException("Not allowed");
//        }

//        // 2️⃣ Invoice → create or update
//        Invoice invoice;
//        if (dto.getInvoiceId() == null || dto.getInvoiceId().isBlank()) {
//            invoice = invoiceService.autoGenerateInvoice(company, dto.getAmount(), dto.getNotes());
//        } else {
//            invoice = invoiceService.applyPayment(dto.getInvoiceId(), dto.getAmount());
//        }

        // 3️⃣ Payment record
        Payment payment = Payment.builder()
                .paymentCode("PAY-" + UUID.randomUUID().toString().substring(0, 8))
                .company(company)
                .amount(dto.getAmount())
                .paymentMethod(dto.getPaymentMethod())
                .effectiveDate(dto.getEffectiveDate() == null ? LocalDate.now() : dto.getEffectiveDate())
                .notes(dto.getNotes())
                .invoiceId(dto.getInvoiceId())
                .paymentDirection(PaymentDirection.CUSTOMER)
                .createdBy(userId)
                .build();

        payment.setPdfUrl("https://dummy.url/payments/" + payment.getPaymentCode() + ".pdf");

        Payment saved = paymentRepo.save(payment);
        Invoice invoice = invoiceService.applyPayment(dto.getInvoiceId(), dto.getAmount());
        postPaymentToAccounting(saved, invoice);
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            salesOrderRepo.findById(invoice.getOrderId())
                    .ifPresent(order -> customerEmailService.sendReceiptEmail(order.getCustomer(), invoice));
        }

//        // 4️⃣TODO: Create transaction
//        transactionService.createTransactionForPayment(
//                saved.getId(),
//                company.getId(),
//                dto.getAmount(),
//                coaService.getCompanyBankAccountCode(company.getId()),
//                coaService.getCustomerARAccountCode(company.getId()),
//                saved.getEffectiveDate(),
//                "PAYMENT"
//        );

        return toDTO(saved);
    }

    private PaymentResponseDTO toDTO(Payment p) {
        PaymentDirection dir = p.getPaymentDirection() != null
                ? p.getPaymentDirection()
                : PaymentDirection.CUSTOMER;
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .paymentCode(p.getPaymentCode())
                .companyId(p.getCompany().getId())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .effectiveDate(p.getEffectiveDate())
                .invoiceId(p.getInvoiceId())
                .paymentDirection(dir.name())
                .purchaseOrderId(p.getPurchaseOrderId())
                .pdfUrl(p.getPdfUrl())
                .createdAt(p.getCreatedAt())
                .build();
    }

    public PaymentResponseDTO getPaymentById(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        return toDTO(p);
    }

    public java.util.List<PaymentResponseDTO> getPaymentsForCompany(Long companyId) {
        return paymentRepo.findByCompanyId(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    public java.util.List<PaymentResponseDTO> getPaymentsForCompany(Long companyId, PaymentDirection direction) {
        if (direction == null) {
            return getPaymentsForCompany(companyId);
        }
        if (direction == PaymentDirection.CUSTOMER) {
            return paymentRepo.findByCompanyId(companyId).stream()
                    .filter(p -> p.getPaymentDirection() == null
                            || p.getPaymentDirection() == PaymentDirection.CUSTOMER)
                    .map(this::toDTO)
                    .toList();
        }
        return paymentRepo.findByCompany_IdAndPaymentDirection(companyId, PaymentDirection.VENDOR).stream()
                .map(this::toDTO)
                .toList();
    }

    public java.util.List<PaymentResponseDTO> getPaymentsByInvoice(String invoiceId) {
        return paymentRepo.findByInvoiceId(invoiceId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public PaymentResponseDTO updatePayment(Long id, CreatePaymentDTO dto) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        if (dto.getPaymentMethod() == null || dto.getPaymentMethod().isBlank()) {
            throw new RuntimeException("Payment method is required");
        }

        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setEffectiveDate(dto.getEffectiveDate() == null ? LocalDate.now() : dto.getEffectiveDate());
        payment.setNotes(dto.getNotes());
        payment.setInvoiceId(dto.getInvoiceId());

        return toDTO(paymentRepo.save(payment));
    }

    @Transactional
    public PaymentResponseDTO confirmPayment(Long id) {
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        PaymentDirection dir = payment.getPaymentDirection() != null
                ? payment.getPaymentDirection()
                : PaymentDirection.CUSTOMER;
        if (dir == PaymentDirection.VENDOR) {
            return confirmVendorPayment(payment);
        }

        if (!"PENDING_REQUEST".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new RuntimeException("Payment is already confirmed");
        }
        if (payment.getInvoiceId() == null || payment.getInvoiceId().isBlank()) {
            throw new RuntimeException("Invoice ID is missing for this payment request");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid payment amount");
        }

        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setEffectiveDate(LocalDate.now());
        payment.setNotes(
                (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                        + "Confirmed from payment request"
        );
        Payment saved = paymentRepo.save(payment);

        Invoice invoice = invoiceService.applyPayment(saved.getInvoiceId(), saved.getAmount());
        postPaymentToAccounting(saved, invoice);
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            salesOrderRepo.findById(invoice.getOrderId())
                    .ifPresent(order -> customerEmailService.sendReceiptEmail(order.getCustomer(), invoice));
        }
        return toDTO(saved);
    }

    private PaymentResponseDTO confirmVendorPayment(Payment payment) {
        if (!"PENDING_VENDOR_PAYMENT".equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new RuntimeException("Vendor payment is already confirmed or is not pending");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid payment amount");
        }
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setEffectiveDate(LocalDate.now());
        payment.setNotes(
                (payment.getNotes() == null ? "" : payment.getNotes() + " | ")
                        + "Vendor payment confirmed (AP)"
        );
        Payment saved = paymentRepo.save(payment);
        postVendorPaymentToAccounting(saved);
        return toDTO(saved);
    }

    /**
     * Posts AP and cash reductions using the PR's credit (AP) account and a company CASH/ASSET account.
     */
    private void postVendorPaymentToAccounting(Payment payment) {
        if (payment.getPurchaseOrderId() == null) {
            throw new RuntimeException("Vendor payment is not linked to a purchase order");
        }
        PurchaseOrder po = purchaseOrderRepo.findById(payment.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Purchase order not found for vendor payment"));
        PurchaseRequisition pr = po.getSourceRequisition();
        if (pr == null || pr.getCreditAccount() == null) {
            throw new RuntimeException(
                    "Cannot post vendor payment to the chart of accounts: this purchase order has no source "
                            + "requisition with an accounts payable (credit) account. Create the PO from an approved PR.");
        }
        Long cashAccountId = resolveDefaultCashAccountId(payment.getCompany().getId());
        transactionService.createVendorPaymentSettlement(
                payment.getId(),
                payment.getCompany().getId(),
                payment.getAmount(),
                pr.getCreditAccount().getId(),
                cashAccountId,
                payment.getEffectiveDate(),
                po.getId());
    }

    private Long resolveDefaultCashAccountId(Long companyId) {
        List<ChartOfAccounts> list = coaRepo.findByCompanyId(companyId);
        return list.stream()
                .filter(a -> a.getType() == COAType.CASH)
                .findFirst()
                .or(() -> list.stream().filter(a -> a.getType() == COAType.ASSET).findFirst())
                .map(ChartOfAccounts::getId)
                .orElseThrow(() -> new RuntimeException(
                        "Add a CASH (or ASSET) chart-of-accounts account for this company to post vendor payments."));
    }

    private void postPaymentToAccounting(Payment payment, Invoice invoice) {
        if (payment.getId() == null || invoice == null) {
            return;
        }
        if (invoice.getOrderId() == null) {
            throw new RuntimeException("Unable to post payment: sales order reference is missing on invoice");
        }
        SalesOrder salesOrder = salesOrderRepo.findById(invoice.getOrderId())
                .orElseThrow(() -> new RuntimeException("Unable to post payment: sales order not found for invoice"));

        Long companyId = payment.getCompany().getId();
        Long debitAccountId = salesOrder.getDebitAccount() != null ? salesOrder.getDebitAccount().getId() : null;
        Long creditAccountId = salesOrder.getCreditAccount() != null ? salesOrder.getCreditAccount().getId() : null;
        if (debitAccountId == null) {
            throw new RuntimeException("Unable to post payment: debit account is missing on sales order");
        }
        if (creditAccountId == null) {
            throw new RuntimeException("Unable to post payment: credit account is missing on sales order");
        }

        transactionService.createTransactionForPayment(
                payment.getId(),
                companyId,
                payment.getAmount(),
                debitAccountId,
                creditAccountId,
                payment.getEffectiveDate(),
                "PAYMENT"
        );
    }

    public void deletePayment(Long id) {
        paymentRepo.deleteById(id);
    }
}
