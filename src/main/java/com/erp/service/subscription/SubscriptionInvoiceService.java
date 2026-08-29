package com.erp.service.subscription;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.subscription.*;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.subscription.SubscriptionInvoiceResponse;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.subscription.CompanySubscriptionRepository;
import com.erp.repo.subscription.SubscriptionInvoiceRepository;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionInvoiceService {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final SubscriptionInvoiceRepository invoiceRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<SubscriptionInvoiceResponse> listForSubscription(Long companySubscriptionId) {
        CompanySubscription cs = subscriptionRepository.findById(companySubscriptionId).orElse(null);
        return invoiceRepository
                .findByCompanySubscriptionIdOrderByCreatedAtDesc(companySubscriptionId)
                .stream()
                .map(inv -> toDto(inv, cs))
                .collect(Collectors.toList());
    }

    /** Create or refresh the current-period invoice snapshot and store its PDF (no email). */
    @Transactional
    public SubscriptionInvoiceResponse generateForCompany(Long companyId) {
        CompanySubscription cs = requireSubscription(companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        SubscriptionInvoice invoice = ensureInvoice(cs);
        if (invoice.isSendSuccess() && invoice.getSentAt() != null) {
            throw new IllegalStateException(
                    "Invoice " + invoice.getInvoiceNo()
                            + " was already sent. Use resend to email it again.");
        }

        syncInvoiceFromSubscription(invoice, cs);
        generateAndStorePdf(invoice, company);
        invoice.setGeneratedAt(Instant.now());
        invoice.setGeneratedBy(currentActor());
        return toDto(invoiceRepository.save(invoice), cs, company);
    }

    /** Re-sync invoice fields from subscription and regenerate PDF (unsent invoices only). */
    @Transactional
    public SubscriptionInvoiceResponse regenerateForCompany(Long companyId) {
        return generateForCompany(companyId);
    }

    /** Email a previously generated invoice PDF to billing recipients. */
    @Transactional
    public SubscriptionInvoiceResponse sendForCompany(Long companyId, boolean resend) {
        CompanySubscription cs = requireSubscription(companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        SubscriptionInvoice invoice = ensureInvoice(cs);
        if (invoice.isSendSuccess() && invoice.getSentAt() != null && !resend) {
            return toDto(invoice, cs, company);
        }
        if (invoice.getGeneratedAt() == null && !(resend && invoice.isSendSuccess())) {
            throw new IllegalStateException(
                    "Generate the invoice first, verify the PDF, then send.");
        }
        if (!resend && isStale(invoice, cs)) {
            throw new IllegalStateException(
                    "Subscription details changed since the invoice was generated. "
                            + "Regenerate the invoice, verify it, then send.");
        }
        if (!emailService.isConfigured()) {
            throw new IllegalStateException(
                    "Email is not configured on this server (MAIL_ENABLED, MAIL_USERNAME, "
                            + "MAIL_PASSWORD, MAIL_FROM). Cannot send subscription invoices.");
        }

        List<String> recipients = resolveInvoiceRecipients(companyId, company);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(
                    "No recipients found: set company/billing email or ensure a company ADMIN has an email.");
        }
        String toJoined = String.join(", ", recipients);

        byte[] pdf = generatePdf(invoice, company);

        String subject = "Subscription invoice " + invoice.getInvoiceNo()
                + " — " + (company.getCompanyName() != null ? company.getCompanyName() : "your company");
        String body = "Please find attached your platform subscription invoice "
                + invoice.getInvoiceNo() + ".\n\n"
                + "Plan: " + invoice.getPlanType() + "\n"
                + "Period: " + invoice.getPeriodStart()
                + " → " + (invoice.getPeriodEnd() != null ? invoice.getPeriodEnd() : "open") + "\n"
                + "Amount: " + formatAmount(invoice.getAmount()) + " "
                + (invoice.getCurrencyCode() != null ? invoice.getCurrencyCode() : "") + "\n";

        try {
            emailService.sendWithPdfAttachment(
                    recipients,
                    subject,
                    body,
                    pdf,
                    invoice.getInvoiceNo() + ".pdf"
            );
            invoice.setToEmail(truncate(toJoined, 500));
            invoice.setSentAt(Instant.now());
            invoice.setSentBy(currentActor());
            invoice.setSendSuccess(true);
            invoice.setSendError(null);
        } catch (Exception e) {
            invoice.setToEmail(truncate(toJoined, 500));
            invoice.setSentAt(Instant.now());
            invoice.setSentBy(currentActor());
            invoice.setSendSuccess(false);
            invoice.setSendError(truncate(e.getMessage(), 1000));
            invoiceRepository.save(invoice);
            throw new IllegalStateException("Failed to send subscription invoice: " + e.getMessage(), e);
        }

        return toDto(invoiceRepository.save(invoice), cs, company);
    }

    /** After subscription edits, keep unsent invoice rows aligned with subscription (marks stale until regenerate). */
    @Transactional
    public void syncUnsentCurrentPeriodInvoice(CompanySubscription cs) {
        if (cs == null || cs.getId() == null) {
            return;
        }
        String periodKey = periodKey(cs.getStartsAt(), cs.getEndsAt());
        invoiceRepository
                .findByCompanySubscriptionIdAndPeriodKey(cs.getId(), periodKey)
                .ifPresent(inv -> {
                    if (inv.isSendSuccess() && inv.getSentAt() != null) {
                        return;
                    }
                    syncInvoiceFromSubscription(inv, cs);
                    inv.setGeneratedAt(null);
                    inv.setGeneratedBy(null);
                    invoiceRepository.save(inv);
                });
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(Long companyId, Long invoiceId) {
        SubscriptionInvoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        return generatePdf(invoice, company);
    }

    @Transactional
    public SubscriptionInvoice ensureInvoice(CompanySubscription cs) {
        String periodKey = periodKey(cs.getStartsAt(), cs.getEndsAt());
        return invoiceRepository
                .findByCompanySubscriptionIdAndPeriodKey(cs.getId(), periodKey)
                .orElseGet(() -> createInvoice(cs, periodKey));
    }

    private SubscriptionInvoice createInvoice(CompanySubscription cs, String periodKey) {
        long seq = invoiceRepository.countByCompanyId(cs.getCompanyId()) + 1;
        String invoiceNo = String.format("SUB-%d-%04d", cs.getCompanyId(), seq);
        SubscriptionInvoice invoice = SubscriptionInvoice.builder()
                .companySubscriptionId(cs.getId())
                .companyId(cs.getCompanyId())
                .invoiceNo(invoiceNo)
                .periodKey(periodKey)
                .periodStart(cs.getStartsAt())
                .periodEnd(cs.getEndsAt())
                .amount(cs.getAmount() != null ? cs.getAmount() : BigDecimal.ZERO)
                .currencyCode(cs.getCurrencyCode())
                .planType(cs.getPlanType())
                .createdAt(Instant.now())
                .createdBy(currentActor())
                .build();
        return invoiceRepository.save(invoice);
    }

    private void syncInvoiceFromSubscription(SubscriptionInvoice invoice, CompanySubscription cs) {
        invoice.setPeriodKey(periodKey(cs.getStartsAt(), cs.getEndsAt()));
        invoice.setPeriodStart(cs.getStartsAt());
        invoice.setPeriodEnd(cs.getEndsAt());
        invoice.setAmount(cs.getAmount() != null ? cs.getAmount() : BigDecimal.ZERO);
        invoice.setCurrencyCode(cs.getCurrencyCode());
        invoice.setPlanType(cs.getPlanType());
    }

    private void generateAndStorePdf(SubscriptionInvoice invoice, Company company) {
        byte[] pdf = generatePdf(invoice, company);
        try {
            MultipartFile pdfFile = new InMemoryMultipartFile(
                    pdf,
                    invoice.getInvoiceNo() + ".pdf",
                    "application/pdf"
            );
            FileUploadResult upload = fileStorageService.upload(
                    pdfFile,
                    FileCategory.SUBSCRIPTION_INVOICE_PDF,
                    String.valueOf(invoice.getId()),
                    true,
                    company.getId()
            );
            invoice.setPdfPath(upload.getBlobPath());
            invoice.setPdfUrl(fileStorageService.getPublicUrl(upload.getBlobPath()));
        } catch (Exception e) {
            log.warn("Subscription invoice PDF upload failed for {}: {}", invoice.getInvoiceNo(), e.getMessage());
        }
    }

    private byte[] generatePdf(SubscriptionInvoice invoice, Company company) {
        try {
            Context context = new Context();
            context.setVariable("invoiceNo", invoice.getInvoiceNo());
            context.setVariable(
                    "companyName",
                    company.getCompanyName() != null ? company.getCompanyName() : "—"
            );
            context.setVariable("planType", invoice.getPlanType() != null ? invoice.getPlanType().name() : "—");
            context.setVariable(
                    "invoiceDate",
                    LocalDate.now().format(DISPLAY_DATE)
            );
            context.setVariable("periodStart", invoice.getPeriodStart().format(DISPLAY_DATE));
            context.setVariable(
                    "periodEnd",
                    invoice.getPeriodEnd() != null
                            ? invoice.getPeriodEnd().format(DISPLAY_DATE)
                            : "open"
            );
            context.setVariable(
                    "currencyCode",
                    invoice.getCurrencyCode() != null ? invoice.getCurrencyCode() : ""
            );
            context.setVariable("amountFormatted", formatAmount(invoice.getAmount()));
            context.setVariable(
                    "generatedAt",
                    Instant.now().toString()
            );

            String html = templateEngine.process("subscription_invoice", context);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Subscription invoice PDF generation failed", e);
        }
    }

    public SubscriptionInvoiceResponse toDto(SubscriptionInvoice inv) {
        CompanySubscription cs = subscriptionRepository
                .findById(inv.getCompanySubscriptionId())
                .orElse(null);
        Company company = companyRepository.findById(inv.getCompanyId()).orElse(null);
        return toDto(inv, cs, company);
    }

    private SubscriptionInvoiceResponse toDto(
            SubscriptionInvoice inv,
            CompanySubscription cs,
            Company company
    ) {
        boolean sent = inv.isSendSuccess() && inv.getSentAt() != null;
        boolean generated = inv.getGeneratedAt() != null;
        boolean stale = cs != null && isStale(inv, cs);
        List<String> recipients = company != null
                ? resolveInvoiceRecipients(inv.getCompanyId(), company)
                : List.of();
        return SubscriptionInvoiceResponse.builder()
                .id(inv.getId())
                .companySubscriptionId(inv.getCompanySubscriptionId())
                .companyId(inv.getCompanyId())
                .invoiceNo(inv.getInvoiceNo())
                .periodStart(inv.getPeriodStart())
                .periodEnd(inv.getPeriodEnd())
                .amount(inv.getAmount())
                .currencyCode(inv.getCurrencyCode())
                .planType(inv.getPlanType())
                .pdfUrl(inv.getPdfUrl())
                .generatedAt(inv.getGeneratedAt())
                .generatedBy(inv.getGeneratedBy())
                .generated(generated)
                .stale(stale)
                .recipientPreview(recipients)
                .toEmail(inv.getToEmail())
                .sentAt(inv.getSentAt())
                .sentBy(inv.getSentBy())
                .sendSuccess(inv.isSendSuccess())
                .sendError(inv.getSendError())
                .sent(sent)
                .createdAt(inv.getCreatedAt())
                .build();
    }

    private SubscriptionInvoiceResponse toDto(SubscriptionInvoice inv, CompanySubscription cs) {
        Company company = companyRepository.findById(inv.getCompanyId()).orElse(null);
        return toDto(inv, cs, company);
    }

    static boolean isStale(SubscriptionInvoice invoice, CompanySubscription cs) {
        if (invoice == null || cs == null) {
            return false;
        }
        if (!Objects.equals(periodKey(cs.getStartsAt(), cs.getEndsAt()), invoice.getPeriodKey())) {
            return true;
        }
        if (!Objects.equals(invoice.getPeriodStart(), cs.getStartsAt())) {
            return true;
        }
        if (!Objects.equals(invoice.getPeriodEnd(), cs.getEndsAt())) {
            return true;
        }
        if (!Objects.equals(invoice.getPlanType(), cs.getPlanType())) {
            return true;
        }
        if (!Objects.equals(invoice.getCurrencyCode(), cs.getCurrencyCode())) {
            return true;
        }
        BigDecimal invAmount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        BigDecimal subAmount = cs.getAmount() != null ? cs.getAmount() : BigDecimal.ZERO;
        return invAmount.compareTo(subAmount) != 0;
    }

    public static String periodKey(LocalDate startsAt, LocalDate endsAt) {
        return startsAt + "_" + (endsAt != null ? endsAt : "open");
    }

    public static SubscriptionPaymentStatus resolvePaymentStatus(
            CompanySubscription cs,
            List<SubscriptionPayment> payments
    ) {
        if (cs.getPlanType() == SubscriptionPlanType.FREE
                || cs.getAmount() == null
                || cs.getAmount().signum() <= 0) {
            return SubscriptionPaymentStatus.NOT_REQUIRED;
        }
        LocalDate periodStart = cs.getStartsAt();
        LocalDate periodEnd = cs.getEndsAt();
        for (SubscriptionPayment p : payments) {
            if (coversCurrentPeriod(p, periodStart, periodEnd)) {
                return SubscriptionPaymentStatus.PAID;
            }
        }
        return SubscriptionPaymentStatus.UNPAID;
    }

    private static boolean coversCurrentPeriod(
            SubscriptionPayment p,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (p.getPeriodStart() != null || p.getPeriodEnd() != null) {
            LocalDate pStart = p.getPeriodStart() != null ? p.getPeriodStart() : p.getPaidOn();
            LocalDate pEnd = p.getPeriodEnd();
            if (pEnd == null && periodEnd == null) {
                return !pStart.isBefore(periodStart);
            }
            if (pEnd == null) {
                return !pStart.isAfter(periodEnd) && !pStart.isBefore(periodStart);
            }
            if (periodEnd == null) {
                return !pEnd.isBefore(periodStart);
            }
            return !pStart.isAfter(periodEnd) && !pEnd.isBefore(periodStart);
        }
        LocalDate paidOn = p.getPaidOn();
        if (paidOn == null) return false;
        if (paidOn.isBefore(periodStart)) return false;
        return periodEnd == null || !paidOn.isAfter(periodEnd);
    }

    private CompanySubscription requireSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription for company " + companyId));
    }

    private List<String> resolveInvoiceRecipients(Long companyId, Company company) {
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
