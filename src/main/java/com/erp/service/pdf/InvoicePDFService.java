package com.erp.service.pdf;

import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyInvoiceSettings;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyInvoiceSettingsRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.hr.InvoiceSettingsDefaults;
import com.erp.service.purchase.PurchaseOrderService;
import com.erp.service.sales.SalesOrderService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InvoicePDFService {
    @Value("${app.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;

    private final TemplateEngine templateEngine;
    private final SalesOrderService salesOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final CompanyRepository companyRepository;
    private final CompanyInvoiceSettingsRepository invoiceSettingsRepository;
    private final AuthContext auth;


    public byte[] generateInvoicePdf(Invoice invoice) {

        try {

            SalesOrderResponseDTO salesOrder = null;
            PurchaseOrderResponseDTO purchaseOrder = null;

            Company company = companyRepository.getReferenceById(auth.getCurrentCompanyId());
            CompanyInvoiceSettings invoiceSettings = getOrCreateInvoiceSettings(company);
            String currencyCode = company.getCurrency() != null
                    ? company.getCurrency().getCurrencyCode()
                    : "";

            if (invoice.getType().name().equals("SALES")) {
                salesOrder = salesOrderService.get(invoice.getOrderId());
            } else {
                purchaseOrder = purchaseOrderService.get(invoice.getOrderId());
            }

            Context context = new Context();
            context.setVariable("invoice", invoice);
            context.setVariable("company", company);
            context.setVariable("salesOrder", salesOrder);
            context.setVariable("purchaseOrder", purchaseOrder);
            context.setVariable("type", invoice.getType());
            context.setVariable("invoiceSettings", invoiceSettings);
            context.setVariable("currencyCode", currencyCode != null ? currencyCode : "");
            context.setVariable("publicInvoiceUrl", buildPublicInvoiceUrl(invoiceSettings, invoice.getInvoiceId()));
            context.setVariable("invoiceTermsList", splitTerms(invoiceSettings.getInvoiceTerms()));
            context.setVariable("items",
                    invoice.getType().name().equals("SALES")
                            ? Objects.requireNonNull(salesOrder).getItems()
                            : Objects.requireNonNull(purchaseOrder).getItems()
            );

            // Render HTML
            String html = templateEngine.process("invoice", context);

            // Convert HTML to PDF
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Invoice PDF generation failed", e);
        }
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
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String normalized = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return normalized + "/public/invoices/" + invoiceCode;
    }

    private CompanyInvoiceSettings getOrCreateInvoiceSettings(Company company) {
        return invoiceSettingsRepository.findByCompanyId(company.getId())
                .orElseGet(() -> invoiceSettingsRepository.save(InvoiceSettingsDefaults.buildDefaults(company)));
    }
}
