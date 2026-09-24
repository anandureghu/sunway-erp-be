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
import java.math.RoundingMode;
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
            boolean isPaid = "PAID".equals(status);
            boolean isPartiallyPaid = "PARTIALLY_PAID".equals(status);
            String statusColor = switch (status) {
                case "PAID" -> "#16a34a";
                case "PARTIALLY_PAID" -> "#d97706";
                case "OVERDUE" -> "#ea580c";
                case "CANCELLED" -> "#64748b";
                default -> "#dc2626";
            };

            String docTitle = isPaid
                    ? "Payment Receipt"
                    : (invoice.getType() != null && "PURCHASE".equalsIgnoreCase(invoice.getType().name())
                    ? "Purchase Invoice"
                    : "Sales Invoice");

            boolean isSales = invoice.getType() != null && "SALES".equalsIgnoreCase(invoice.getType().name());
            String partyLabel = isSales ? "Bill To" : "Supplier";
            String partyName = isSales
                    ? firstNonBlank(salesOrder != null ? salesOrder.getCustomerName() : null, invoice.getToParty(), "—")
                    : firstNonBlank(purchaseOrder != null ? purchaseOrder.getSupplierName() : null, invoice.getToParty(), "—");
            String partyEmail = isSales && salesOrder != null
                    ? nullToEmpty(salesOrder.getCustomerEmail())
                    : (!isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierEmail()) : "");
            String partyPhone = isSales && salesOrder != null
                    ? nullToEmpty(salesOrder.getCustomerPhone())
                    : (!isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierPhone()) : "");
            String partyAddress = isSales && salesOrder != null
                    ? nullToEmpty(salesOrder.getCustomerAddress())
                    : (!isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierAddress()) : "");
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
            String headerSubtitleText = applyTemplate(invoiceSettings.getInvoiceHeaderSubtitle(),
                    company, invoiceDateFormatted, dueDateFormatted, paidDateFormatted, invoice.getInvoiceId());

            Context context = new Context();
            context.setVariable("invoice", invoice);
            context.setVariable("company", company);
            context.setVariable("invoiceSettings", invoiceSettings);
            context.setVariable("currencyCode", currencyCode);
            String publicInvoiceUrl = buildPublicInvoiceUrl(invoiceSettings, invoice.getInvoiceId());
            context.setVariable("publicInvoiceUrl", publicInvoiceUrl);
            context.setVariable("showQr", publicInvoiceUrl != null);
            context.setVariable("qrImageUrl", publicInvoiceUrl != null ? buildQrImageUrl(publicInvoiceUrl) : null);
            String footerAddress = buildAddress(company.getStreet(), company.getCity(),
                    company.getState(), company.getCountry());
            context.setVariable("footerAddress", footerAddress);
            context.setVariable("footerSupportEmail",
                    isNotBlank(invoiceSettings.getInvoiceFooterSupportEmail())
                            ? invoiceSettings.getInvoiceFooterSupportEmail()
                            : company.getCompanyEmail());
            context.setVariable("footerBillingEmail",
                    isNotBlank(invoiceSettings.getInvoiceFooterBillingEmail())
                            ? invoiceSettings.getInvoiceFooterBillingEmail()
                            : company.getBillingEmail());
            context.setVariable("footerWebsiteUrl", company.getWebsiteUrl());
            context.setVariable("invoiceTermsList", splitTerms(invoiceSettings.getInvoiceTerms()));
            context.setVariable("invoiceDateFormatted", invoiceDateFormatted);
            context.setVariable("dueDateFormatted", dueDateFormatted);
            context.setVariable("paidDateFormatted", paidDateFormatted);
            context.setVariable("statusColor", statusColor);
            context.setVariable("statusText", status);
            context.setVariable("docTitle", docTitle);
            context.setVariable("isSales", isSales);
            context.setVariable("isPaid", isPaid);
            context.setVariable("showPurchaseBadge", !isSales);
            context.setVariable("headerSubtitleText", headerSubtitleText);
            context.setVariable("showHeaderSubtitle", headerSubtitleText != null && !headerSubtitleText.isBlank());
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
            context.setVariable("showPaidDate",
                    (isPaid || isPartiallyPaid) && invoice.getPaidDate() != null);
            context.setVariable("paidDateLabel", isPaid ? "Paid Date" : "Last Payment");
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
            String discountLabel = formatMoney(discountAmount, currencyCode);
            if (isPositive(discountAmount) && isPositive(grossSubtotal)) {
                BigDecimal pct = discountAmount
                        .multiply(BigDecimal.valueOf(100))
                        .divide(grossSubtotal, 2, RoundingMode.HALF_UP);
                // Prefer a shared line discount % when every sales line matches.
                BigDecimal sharedPct = null;
                if (isSales && salesOrder != null && salesOrder.getItems() != null
                        && !salesOrder.getItems().isEmpty()) {
                    BigDecimal first = null;
                    boolean allSame = true;
                    for (SalesOrderItemResponseDTO line : salesOrder.getItems()) {
                        BigDecimal p = line.getDiscountPercent() != null
                                ? line.getDiscountPercent()
                                : BigDecimal.ZERO;
                        if (first == null) first = p;
                        else if (first.compareTo(p) != 0) {
                            allSame = false;
                            break;
                        }
                    }
                    if (allSame && first != null && first.compareTo(BigDecimal.ZERO) > 0) {
                        sharedPct = first;
                    }
                }
                BigDecimal shown = sharedPct != null ? sharedPct : pct;
                discountLabel = discountLabel + " (" + formatDecimal(shown) + "%)";
            }
            context.setVariable("discountFormatted", discountLabel);
            context.setVariable("taxFormatted", formatMoney(invoice.getTaxAmount(), currencyCode));
            context.setVariable("totalFormatted", formatMoney(invoice.getAmount(), currencyCode));
            context.setVariable("showDiscount", isPositive(discountAmount));
            context.setVariable("showTax", isPositive(invoice.getTaxAmount()));
            context.setVariable("showPaymentInfo",
                    isSales && invoice.getBankAccount() != null && !isPaid);
            if (invoice.getBankAccount() != null) {
                String bankIbanNumber = isNotBlank(invoice.getBankAccount().getAccountNumber())
                        ? invoice.getBankAccount().getAccountNumber()
                        : invoice.getBankAccount().getIban();
                context.setVariable("bankIbanNumber", bankIbanNumber);
                String bankAccountHolder = isNotBlank(invoice.getBankAccount().getAccountHolderName())
                        ? invoice.getBankAccount().getAccountHolderName()
                        : company.getCompanyName();
                context.setVariable("bankAccountHolder", bankAccountHolder);
            }
            context.setVariable("showNotes", isSales && notesText != null && !notesText.isBlank());
            // Terms are configured for unpaid invoices only — hide on paid invoices / receipts.
            context.setVariable("showTerms",
                    isSales && !isPaid
                            && invoiceSettings.getInvoiceTerms() != null
                            && !splitTerms(invoiceSettings.getInvoiceTerms()).isEmpty());
            context.setVariable("sigPartyName", partyName);
            context.setVariable("sigPartyRole", isSales ? "Customer Signature and Date" : "Supplier Signature and Date");

            boolean showSupplierBankDetails = !isSales
                    && purchaseOrder != null
                    && ((purchaseOrder.getSupplierBankName() != null && !purchaseOrder.getSupplierBankName().isBlank())
                        || (purchaseOrder.getSupplierIban() != null && !purchaseOrder.getSupplierIban().isBlank())
                        || (purchaseOrder.getSupplierCurrencyCode() != null
                            && !purchaseOrder.getSupplierCurrencyCode().isBlank()));
            context.setVariable("showSupplierBankDetails", showSupplierBankDetails);
            context.setVariable("supplierBankName",
                    !isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierBankName()) : "");
            context.setVariable("supplierIban",
                    !isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierIban()) : "");
            context.setVariable("supplierCurrencyCode",
                    !isSales && purchaseOrder != null ? nullToEmpty(purchaseOrder.getSupplierCurrencyCode()) : "");

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
        BigDecimal unit = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        int qty = item.getQuantity() != null ? item.getQuantity() : 0;
        BigDecimal gross = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String discount = "—";
        if (item.getDiscountPercent() != null && item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
            discount = formatDecimal(item.getDiscountPercent()) + "%";
        }
        return InvoiceLineView.builder()
                .index(index)
                .name(firstNonBlank(item.getItemName(), "—"))
                .description(nullToEmpty(item.getItemDescription()))
                .quantityFormatted(String.valueOf(qty))
                .unitFormatted(formatMoney(unit, currencyCode))
                .discountFormatted(discount)
                // Exact line item amount before discount (unit × qty).
                .amountFormatted(formatMoney(gross, currencyCode))
                .build();
    }

    private InvoiceLineView toPurchaseLine(int index, PurchaseOrderItemDTO item, String currencyCode) {
        BigDecimal unit = item.getUnitCost() != null ? item.getUnitCost() : item.getUnitPrice();
        return InvoiceLineView.builder()
                .index(index)
                .name(firstNonBlank(item.getItemName(), "—"))
                .description(nullToEmpty(item.getItemDescription()))
                .quantityFormatted(purchaseLineQuantity(item, unit))
                .unitFormatted(formatMoney(unit, currencyCode))
                .discountFormatted("—")
                .amountFormatted(formatMoney(item.getLineTotal(), currencyCode))
                .build();
    }

    /**
     * Mirrors frontend purchaseInvoiceLineQuantity (lib/purchase-line-item.ts): shows the
     * quantity implied by the line total when it diverges from ordered qty × unit (e.g. after
     * inspection adjustments), falling back to received quantity, then ordered quantity.
     */
    private static String purchaseLineQuantity(PurchaseOrderItemDTO item, BigDecimal unit) {
        int orderedQty = item.getQuantity() != null ? item.getQuantity() : 0;
        BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
        if (unit != null && unit.compareTo(BigDecimal.ZERO) > 0 && lineTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal orderedTotal = unit.multiply(BigDecimal.valueOf(orderedQty));
            if (lineTotal.subtract(orderedTotal).abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                BigDecimal implied = lineTotal.divide(unit, 3, RoundingMode.HALF_UP);
                BigDecimal rounded = implied.setScale(0, RoundingMode.HALF_UP);
                if (implied.subtract(rounded).abs().compareTo(BigDecimal.valueOf(0.001)) < 0) {
                    return String.valueOf(rounded.intValueExact());
                }
                return implied.stripTrailingZeros().toPlainString();
            }
        }
        Integer received = item.getReceivedQty();
        if (received != null && received > 0 && !received.equals(orderedQty)) {
            return String.valueOf(received);
        }
        return String.valueOf(orderedQty);
    }

    private String buildNotes(
            Invoice invoice,
            CompanyInvoiceSettings settings,
            Company company,
            String invoiceDateFormatted,
            String dueDateFormatted,
            String paidDateFormatted
    ) {
        String template = "PAID".equalsIgnoreCase(invoice.getStatus())
                ? settings.getInvoiceNotesPaid()
                : settings.getInvoiceNotesUnpaid();
        return applyTemplate(template, company, invoiceDateFormatted, dueDateFormatted,
                paidDateFormatted, invoice.getInvoiceId());
    }

    private static String applyTemplate(
            String template,
            Company company,
            String invoiceDateFormatted,
            String dueDateFormatted,
            String paidDateFormatted,
            String invoiceId
    ) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{{companyName}}", nullToEmpty(company.getCompanyName()))
                .replace("{{invoiceDate}}", invoiceDateFormatted)
                .replace("{{dueDate}}", dueDateFormatted)
                .replace("{{paidDate}}", paidDateFormatted)
                .replace("{{invoiceId}}", nullToEmpty(invoiceId));
    }

    /** Joins non-blank parts with ", "; returns null (not an empty string) when everything is blank. */
    private static String buildAddress(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(part.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String buildQrImageUrl(String data) {
        String encoded = java.net.URLEncoder.encode(data, java.nio.charset.StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + encoded;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
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
        String quantityFormatted;
        String unitFormatted;
        String discountFormatted;
        String amountFormatted;
    }
}