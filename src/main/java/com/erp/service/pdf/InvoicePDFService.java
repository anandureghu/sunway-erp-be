package com.erp.service.pdf;

import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyInvoiceSettings;
import com.erp.dto.purchase.PurchaseOrderItemDTO;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderItemResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.repo.hr.CompanyInvoiceSettingsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.hr.InvoiceSettingsDefaults;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.sales.SalesOrderService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePDFService {
    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;

    private final TemplateEngine templateEngine;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final CompanyRepository companyRepository;
    private final CompanyInvoiceSettingsRepository invoiceSettingsRepository;
    private final AuthContext auth;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy");

    public byte[] generateInvoicePdf(Invoice invoice) {
        try {
            Long companyId = invoice.getCompany() != null
                    ? invoice.getCompany().getId()
                    : auth.getCurrentCompanyId();
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("Company not found for invoice PDF"));
            // Initialize lazy currency while session is open.
            String currencyCode = "";
            if (company.getCurrency() != null) {
                currencyCode = company.getCurrency().getCurrencyCode();
                if (currencyCode == null) {
                    currencyCode = "";
                }
            }

            CompanyInvoiceSettings invoiceSettings = getOrCreateInvoiceSettings(company);

            SalesOrderResponseDTO salesOrder = null;
            PurchaseOrderResponseDTO purchaseOrder = null;
            List<InvoiceLineView> lines = new ArrayList<>();

            if (invoice.getType() != null && "SALES".equalsIgnoreCase(invoice.getType().name())) {
                if (invoice.getOrderId() == null) {
                    throw new RuntimeException("Sales invoice is missing orderId");
                }
                salesOrder = salesOrderService.get(invoice.getOrderId());
                if (salesOrder.getItems() != null) {
                    int i = 1;
                    for (SalesOrderItemResponseDTO item : salesOrder.getItems()) {
                        lines.add(toSalesLine(i++, item, currencyCode));
                    }
                }
            } else {
                if (invoice.getOrderId() == null) {
                    throw new RuntimeException("Purchase invoice is missing orderId");
                }
                purchaseOrder = purchaseOrderService.get(invoice.getOrderId());
                if (purchaseOrder.getItems() != null) {
                    int i = 1;
                    for (PurchaseOrderItemDTO item : purchaseOrder.getItems()) {
                        lines.add(toPurchaseLine(i++, item, currencyCode));
                    }
                }
            }

            String status = invoice.getStatus() != null ? invoice.getStatus().toUpperCase(Locale.ROOT) : "UNPAID";
            String statusColor = switch (status) {
                case "PAID" -> "#16a34a";
                case "PARTIALLY_PAID" -> "#d97706";
                case "OVERDUE" -> "#ea580c";
                case "CANCELLED" -> "#64748b";
                default -> "#dc2626";
            };

            String docTitle = "PAID".equals(status)
                    ? "Payment Receipt"
                    : (invoice.getType() != null && "PURCHASE".equalsIgnoreCase(invoice.getType().name())
                    ? "Purchase Invoice"
                    : "Sales Invoice");

            boolean isSales = invoice.getType() != null && "SALES".equalsIgnoreCase(invoice.getType().name());
            String partyLabel = isSales ? "Bill To" : "Supplier";
            String partyName = isSales
                    ? firstNonBlank(salesOrder != null ? salesOrder.getCustomerName() : null, invoice.getToParty(), "—")
                    : firstNonBlank(purchaseOrder != null ? purchaseOrder.getSupplierName() : null, invoice.getToParty(), "—");
            String partyEmail = isSales && salesOrder != null ? nullToEmpty(salesOrder.getCustomerEmail()) : "";
            String partyPhone = isSales && salesOrder != null ? nullToEmpty(salesOrder.getCustomerPhone()) : "";
            String partyAddress = isSales && salesOrder != null ? nullToEmpty(salesOrder.getCustomerAddress()) : "";
            String orderLabel = isSales ? "Sales Order" : "Purchase Order";
            String orderNumber = isSales
                    ? (salesOrder != null ? nullToDash(salesOrder.getOrderNumber()) : "—")
                    : (purchaseOrder != null ? nullToDash(purchaseOrder.getOrderNumber()) : "—");

            String invoiceDateFormatted = invoice.getInvoiceDate() != null
                    ? invoice.getInvoiceDate().format(DATE_FMT) : "—";
            String dueDateFormatted = invoice.getDueDate() != null
                    ? invoice.getDueDate().format(DATE_FMT) : "—";
            String paidDateFormatted = invoice.getPaidDate() != null
                    ? invoice.getPaidDate().format(DATE_FMT) : "—";

            String notesText = buildNotes(invoice, invoiceSettings, company,
                    invoiceDateFormatted, dueDateFormatted, paidDateFormatted);

            Context context = new Context();
            context.setVariable("invoice", invoice);
            context.setVariable("company", company);
            context.setVariable("invoiceSettings", invoiceSettings);
            context.setVariable("currencyCode", currencyCode);
            context.setVariable("publicInvoiceUrl", buildPublicInvoiceUrl(invoiceSettings, invoice.getInvoiceId()));
            context.setVariable("invoiceTermsList", splitTerms(invoiceSettings.getInvoiceTerms()));
            context.setVariable("invoiceDateFormatted", invoiceDateFormatted);
            context.setVariable("dueDateFormatted", dueDateFormatted);
            context.setVariable("paidDateFormatted", paidDateFormatted);
            context.setVariable("statusColor", statusColor);
            context.setVariable("statusText", status);
            context.setVariable("docTitle", docTitle);
            context.setVariable("isSales", isSales);
            context.setVariable("isPaid", "PAID".equals(status));
            context.setVariable("partyLabel", partyLabel);
            context.setVariable("partyName", partyName);
            context.setVariable("partyEmail", partyEmail);
            context.setVariable("partyPhone", partyPhone);
            context.setVariable("partyAddress", partyAddress);
            context.setVariable("orderLabel", orderLabel);
            context.setVariable("orderNumber", orderNumber);
            context.setVariable("supplierInvoiceNumber", invoice.getSupplierInvoiceNumber());
            context.setVariable("showSupplierInvoiceNumber",
                    !isSales && invoice.getSupplierInvoiceNumber() != null
                            && !invoice.getSupplierInvoiceNumber().isBlank());
            context.setVariable("lines", lines);
            context.setVariable("notesText", notesText);
            // subtotalAmount is stored post-discount; show pre-discount gross in the PDF summary.
            BigDecimal discountAmount = nullToZero(invoice.getDiscountAmount());
            BigDecimal netSubtotal = nullToZero(invoice.getSubtotalAmount());
            if (netSubtotal.compareTo(BigDecimal.ZERO) == 0 && invoice.getAmount() != null) {
                netSubtotal = invoice.getAmount().subtract(nullToZero(invoice.getTaxAmount()));
                if (netSubtotal.compareTo(BigDecimal.ZERO) < 0) {
                    netSubtotal = BigDecimal.ZERO;
                }
            }
            BigDecimal grossSubtotal = netSubtotal.add(discountAmount);
            context.setVariable("subtotalFormatted", formatMoney(grossSubtotal, currencyCode));
            context.setVariable("discountFormatted", formatMoney(discountAmount, currencyCode));
            context.setVariable("taxFormatted", formatMoney(invoice.getTaxAmount(), currencyCode));
            context.setVariable("totalFormatted", formatMoney(invoice.getAmount(), currencyCode));
            context.setVariable("showDiscount", isPositive(discountAmount));
            context.setVariable("showTax", isPositive(invoice.getTaxAmount()));
            context.setVariable("showPaymentInfo",
                    isSales && invoice.getBankAccount() != null && !"PAID".equals(status));
            context.setVariable("showNotes", isSales && notesText != null && !notesText.isBlank());
            context.setVariable("showTerms",
                    isSales && invoiceSettings.getInvoiceTerms() != null
                            && !splitTerms(invoiceSettings.getInvoiceTerms()).isEmpty());
            context.setVariable("sigPartyName", partyName);
            context.setVariable("sigPartyRole", isSales ? "Customer Signature and Date" : "Supplier Signature and Date");

            // Touch bank account fields while session is open.
            if (invoice.getBankAccount() != null) {
                invoice.getBankAccount().getBankName();
                invoice.getBankAccount().getAccountHolderName();
                invoice.getBankAccount().getAccountNumber();
                invoice.getBankAccount().getIfscCode();
                invoice.getBankAccount().getBranchName();
            }

            String html = templateEngine.process("invoice", context);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Invoice PDF generation failed for invoiceId={}", invoice.getInvoiceId(), e);
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            throw new RuntimeException(
                    "Invoice PDF generation failed: " + root.getMessage(), e);
        }
    }

    private InvoiceLineView toSalesLine(int index, SalesOrderItemResponseDTO item, String currencyCode) {
        String discount = item.getDiscountPercent() != null
                ? formatDecimal(item.getDiscountPercent()) + "%"
                : "—";
        return InvoiceLineView.builder()
                .index(index)
                .name(firstNonBlank(item.getItemName(), "—"))
                .description(nullToEmpty(item.getItemDescription()))
                .quantity(item.getQuantity() != null ? item.getQuantity() : 0)
                .unitFormatted(formatMoney(item.getUnitPrice(), currencyCode))
                .discountFormatted(discount)
                .amountFormatted(formatMoney(item.getLineTotal(), currencyCode))
                .build();
    }

    private InvoiceLineView toPurchaseLine(int index, PurchaseOrderItemDTO item, String currencyCode) {
        BigDecimal unit = item.getUnitPrice() != null ? item.getUnitPrice() : item.getUnitCost();
        return InvoiceLineView.builder()
                .index(index)
                .name(firstNonBlank(item.getItemName(), "—"))
                .description(nullToEmpty(item.getItemDescription()))
                .quantity(item.getQuantity() != null ? item.getQuantity() : 0)
                .unitFormatted(formatMoney(unit, currencyCode))
                .discountFormatted("—")
                .amountFormatted(formatMoney(item.getLineTotal(), currencyCode))
                .build();
    }

    private String buildNotes(
            Invoice invoice,
            CompanyInvoiceSettings settings,
            Company company,
            String invoiceDateFormatted,
            String dueDateFormatted,
            String paidDateFormatted
    ) {
        String template;
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            template = settings.getInvoiceNotesPaid();
        } else {
            template = settings.getInvoiceNotesUnpaid();
        }
        if (template == null) {
            template = "";
        }
        return template
                .replace("{{companyName}}", nullToEmpty(company.getCompanyName()))
                .replace("{{invoiceDate}}", invoiceDateFormatted)
                .replace("{{dueDate}}", dueDateFormatted)
                .replace("{{paidDate}}", paidDateFormatted)
                .replace("{{invoiceId}}", nullToEmpty(invoice.getInvoiceId()));
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String formatMoney(BigDecimal value, String currencyCode) {
        String amount = formatDecimal(value != null ? value : BigDecimal.ZERO);
        if (currencyCode == null || currencyCode.isBlank()) {
            return amount;
        }
        return currencyCode + " " + amount;
    }

    private static String formatDecimal(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(value != null ? value : BigDecimal.ZERO);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "—";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "—";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private List<String> splitTerms(String terms) {
        if (terms == null || terms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(terms.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private String buildPublicInvoiceUrl(CompanyInvoiceSettings invoiceSettings, String invoiceCode) {
        if (!invoiceSettings.isInvoiceQrEnabled()) {
            return null;
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank() || invoiceCode == null || invoiceCode.isBlank()) {
            return null;
        }
        String normalized = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return normalized + "/public/invoices/" + invoiceCode;
    }

    private CompanyInvoiceSettings getOrCreateInvoiceSettings(Company company) {
        return invoiceSettingsRepository.findByCompanyId(company.getId())
                .orElseGet(() -> invoiceSettingsRepository.save(InvoiceSettingsDefaults.buildDefaults(company)));
    }

    @lombok.Value
    @Builder
    public static class InvoiceLineView {
        int index;
        String name;
        String description;
        int quantity;
        String unitFormatted;
        String discountFormatted;
        String amountFormatted;
    }
}