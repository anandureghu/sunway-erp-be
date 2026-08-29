package com.erp.service.subscription;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.subscription.SubscriptionInvoice;
import com.erp.domain.subscription.SubscriptionPayment;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.subscription.SubscriptionPaymentResponse;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.subscription.SubscriptionInvoiceRepository;
import com.erp.repo.subscription.SubscriptionPaymentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.notification.EmailService;
import com.erp.util.InMemoryMultipartFile;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPaymentReceiptService {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final AuthContext authContext;

    @Transactional
    public SubscriptionPayment generateReceipt(SubscriptionPayment payment) {
        if (payment.getReceiptGeneratedAt() != null && payment.getReceiptNo() != null) {
            return payment;
        }
        Company company = companyRepository.findById(payment.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        payment.setReceiptNo(String.format("SUBR-%d-%05d", payment.getCompanyId(), payment.getId()));
        generateAndStoreReceiptPdf(payment, company);
        payment.setReceiptGeneratedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    @Transactional
    public SubscriptionPaymentResponse sendReceipt(Long companyId, Long paymentId, boolean resend) {
        SubscriptionPayment payment = paymentRepository.findByIdAndCompanyId(paymentId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (payment.getReceiptGeneratedAt() == null) {
            payment = generateReceipt(payment);
        }
        if (payment.isReceiptSendSuccess() && payment.getReceiptSentAt() != null && !resend) {
            return toDto(payment);
        }
        if (!emailService.isConfigured()) {
            throw new IllegalStateException(
                    "Email is not configured on this server (MAIL_ENABLED, MAIL_USERNAME, "
                            + "MAIL_PASSWORD, MAIL_FROM). Cannot send payment receipts.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        List<String> recipients = resolveReceiptRecipients(companyId, company);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(
                    "No recipients found: set company/billing email or ensure a company ADMIN has an email.");
        }
        String toJoined = String.join(", ", recipients);
        byte[] pdf = generateReceiptPdf(payment, company);

        String subject = "Payment receipt " + payment.getReceiptNo()
                + " — " + (company.getCompanyName() != null ? company.getCompanyName() : "your company");
        String body = "Please find attached your subscription payment receipt "
                + payment.getReceiptNo() + ".\n\n"
                + "Amount received: " + formatAmount(payment.getAmount()) + "\n"
                + "Paid on: " + payment.getPaidOn() + "\n";

        try {
            emailService.sendWithPdfAttachment(
                    recipients,
                    subject,
                    body,
                    pdf,
                    payment.getReceiptNo() + ".pdf"
            );
            payment.setReceiptToEmail(truncate(toJoined, 500));
            payment.setReceiptSentAt(Instant.now());
            payment.setReceiptSentBy(currentActor());
            payment.setReceiptSendSuccess(true);
            payment.setReceiptSendError(null);
        } catch (Exception e) {
            payment.setReceiptToEmail(truncate(toJoined, 500));
            payment.setReceiptSentAt(Instant.now());
            payment.setReceiptSentBy(currentActor());
            payment.setReceiptSendSuccess(false);
            payment.setReceiptSendError(truncate(e.getMessage(), 1000));
            paymentRepository.save(payment);
            throw new IllegalStateException("Failed to send payment receipt: " + e.getMessage(), e);
        }

        return toDto(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public byte[] downloadReceiptPdf(Long companyId, Long paymentId) {
        SubscriptionPayment payment = paymentRepository.findByIdAndCompanyId(paymentId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        return generateReceiptPdf(payment, company);
    }

    public SubscriptionPaymentResponse toDto(SubscriptionPayment payment) {
        String invoiceNo = null;
        if (payment.getSubscriptionInvoiceId() != null) {
            invoiceNo = invoiceRepository.findById(payment.getSubscriptionInvoiceId())
                    .map(SubscriptionInvoice::getInvoiceNo)
                    .orElse(null);
        }
        boolean receiptGenerated = payment.getReceiptGeneratedAt() != null;
        boolean receiptSent = payment.isReceiptSendSuccess() && payment.getReceiptSentAt() != null;
        return SubscriptionPaymentResponse.builder()
                .id(payment.getId())
                .companySubscriptionId(payment.getCompanySubscriptionId())
                .companyId(payment.getCompanyId())
                .invoiceId(payment.getSubscriptionInvoiceId())
                .invoiceNo(invoiceNo)
                .amount(payment.getAmount())
                .paidOn(payment.getPaidOn())
                .methodNote(payment.getMethodNote())
                .periodStart(payment.getPeriodStart())
                .periodEnd(payment.getPeriodEnd())
                .recordedBy(payment.getRecordedBy())
                .createdAt(payment.getCreatedAt())
                .receiptNo(payment.getReceiptNo())
                .receiptGeneratedAt(payment.getReceiptGeneratedAt())
                .receiptSentAt(payment.getReceiptSentAt())
                .receiptGenerated(receiptGenerated)
                .receiptSent(receiptSent)
                .receiptToEmail(payment.getReceiptToEmail())
                .receiptSendError(payment.getReceiptSendError())
                .build();
    }

    private void generateAndStoreReceiptPdf(SubscriptionPayment payment, Company company) {
        byte[] pdf = generateReceiptPdf(payment, company);
        try {
            MultipartFile pdfFile = new InMemoryMultipartFile(
                    pdf,
                    payment.getReceiptNo() + ".pdf",
                    "application/pdf"
            );
            FileUploadResult upload = fileStorageService.upload(
                    pdfFile,
                    FileCategory.SUBSCRIPTION_RECEIPT_PDF,
                    String.valueOf(payment.getId()),
                    true,
                    company.getId()
            );
            log.info("Subscription receipt stored for payment {} at {}", payment.getId(), upload.getBlobPath());
        } catch (Exception e) {
            log.warn("Subscription receipt PDF upload failed for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private byte[] generateReceiptPdf(SubscriptionPayment payment, Company company) {
        SubscriptionInvoice invoice = payment.getSubscriptionInvoiceId() != null
                ? invoiceRepository.findById(payment.getSubscriptionInvoiceId()).orElse(null)
                : null;
        try {
            Context context = new Context();
            context.setVariable("receiptNo", payment.getReceiptNo() != null
                    ? payment.getReceiptNo()
                    : "SUBR-" + payment.getId());
            context.setVariable(
                    "companyName",
                    company.getCompanyName() != null ? company.getCompanyName() : "—"
            );
            context.setVariable(
                    "planType",
                    invoice != null && invoice.getPlanType() != null
                            ? invoice.getPlanType().name()
                            : "—"
            );
            context.setVariable("paidOn", payment.getPaidOn().format(DISPLAY_DATE));
            context.setVariable(
                    "invoiceNo",
                    invoice != null ? invoice.getInvoiceNo() : "—"
            );
            LocalDate pStart = payment.getPeriodStart() != null
                    ? payment.getPeriodStart()
                    : (invoice != null ? invoice.getPeriodStart() : null);
            LocalDate pEnd = payment.getPeriodEnd() != null
                    ? payment.getPeriodEnd()
                    : (invoice != null ? invoice.getPeriodEnd() : null);
            context.setVariable(
                    "periodStart",
                    pStart != null ? pStart.format(DISPLAY_DATE) : "—"
            );
            context.setVariable(
                    "periodEnd",
                    pEnd != null ? pEnd.format(DISPLAY_DATE) : "open"
            );
            context.setVariable(
                    "methodNote",
                    payment.getMethodNote() != null && !payment.getMethodNote().isBlank()
                            ? payment.getMethodNote()
                            : "—"
            );
            context.setVariable(
                    "currencyCode",
                    invoice != null && invoice.getCurrencyCode() != null
                            ? invoice.getCurrencyCode()
                            : (company.getCurrency() != null ? company.getCurrency().getCurrencyCode() : "")
            );
            context.setVariable("amountFormatted", formatAmount(payment.getAmount()));
            context.setVariable("generatedAt", Instant.now().toString());

            String html = templateEngine.process("subscription_receipt", context);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Subscription receipt PDF generation failed", e);
        }
    }

    private List<String> resolveReceiptRecipients(Long companyId, Company company) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        addEmail(emails, billingEmail(company));
        for (User admin : userRepository.findAdminsForCompany(companyId)) {
            addEmail(emails, admin.getEmail());
        }
        return new ArrayList<>(emails);
    }

    private static void addEmail(LinkedHashSet<String> emails, String raw) {
        if (raw == null) return;
        String email = raw.trim();
        if (email.isEmpty()) return;
        for (String existing : emails) {
            if (existing.equalsIgnoreCase(email)) {
                return;
            }
        }
        emails.add(email);
    }

    private String billingEmail(Company company) {
        if (company == null) return null;
        if (company.getBillingEmail() != null && !company.getBillingEmail().isBlank()) {
            return company.getBillingEmail();
        }
        return company.getCompanyEmail();
    }

    private String currentActor() {
        Long id = authContext.getCurrentUserId();
        return id != null ? String.valueOf(id) : "system";
    }

    private static String formatAmount(BigDecimal amount) {
        BigDecimal v = amount != null ? amount : BigDecimal.ZERO;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
