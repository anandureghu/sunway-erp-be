package com.erp.service.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;
    private final InvoiceService invoiceService;
    private final TransactionService transactionService;
    private final ChartOfAccountsService coaService;

    public PaymentService(PaymentRepository paymentRepo,
                          CompanyRepository companyRepo,
                          AuthContext auth,
                          InvoiceService invoiceService,
                          TransactionService transactionService,
                          ChartOfAccountsService coaService) {

        this.paymentRepo = paymentRepo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.invoiceService = invoiceService;
        this.transactionService = transactionService;
        this.coaService = coaService;
    }

    @Transactional
    public PaymentResponseDTO createPayment(CreatePaymentDTO dto) {

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long userId = auth.getCurrentUserId();

//        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
//            throw new RuntimeException("Not allowed");
//        }

        // 2️⃣ Invoice → create or update
        Invoice invoice;
        if (dto.getInvoiceId() == null || dto.getInvoiceId().isBlank()) {
            invoice = invoiceService.autoGenerateInvoice(company, dto.getAmount(), dto.getNotes());
        } else {
            invoice = invoiceService.applyPayment(dto.getInvoiceId(), dto.getAmount());
        }

        // 3️⃣ Payment record
        Payment payment = Payment.builder()
                .paymentCode("PAY-" + UUID.randomUUID().toString().substring(0, 8))
                .company(company)
                .amount(dto.getAmount())
                .paymentMethod(dto.getPaymentMethod())
                .effectiveDate(dto.getEffectiveDate() == null ? LocalDate.now() : dto.getEffectiveDate())
                .notes(dto.getNotes())
                .invoiceId(invoice.getInvoiceId())
                .createdBy(userId)
                .build();

        payment.setPdfUrl("https://dummy.url/payments/" + payment.getPaymentCode() + ".pdf");

        Payment saved = paymentRepo.save(payment);

        // 4️⃣ Create transaction
        transactionService.createTransactionForPayment(
                saved.getId(),
                company.getId(),
                dto.getAmount(),
                coaService.getCompanyBankAccountCode(company.getId()),
                coaService.getCustomerARAccountCode(company.getId()),
                saved.getEffectiveDate(),
                "PAYMENT"
        );

        return toDTO(saved, invoice);
    }

    private PaymentResponseDTO toDTO(Payment p, Invoice inv) {
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .paymentCode(p.getPaymentCode())
                .companyId(p.getCompany().getId())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .effectiveDate(p.getEffectiveDate())
                .invoiceId(inv.getInvoiceId())
                .pdfUrl(p.getPdfUrl())
                .createdAt(p.getCreatedAt())
                .build();
    }

    public PaymentResponseDTO getPaymentById(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        Invoice inv = invoiceService.getByInvoiceId(p.getInvoiceId());
        return toDTO(p, inv);
    }

    public java.util.List<PaymentResponseDTO> getPaymentsForCompany(Long companyId) {
        return paymentRepo.findByCompanyId(companyId).stream()
                .map(p -> toDTO(p, invoiceService.getByInvoiceId(p.getInvoiceId())))
                .toList();
    }

    public java.util.List<PaymentResponseDTO> getPaymentsByInvoice(String invoiceId) {
        return paymentRepo.findByInvoiceId(invoiceId).stream()
                .map(p -> toDTO(p, invoiceService.getByInvoiceId(invoiceId)))
                .toList();
    }

    public void deletePayment(Long id) {
        paymentRepo.deleteById(id);
    }
}
