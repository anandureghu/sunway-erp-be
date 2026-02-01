package com.erp.service.pdf;

import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.sales.SalesOrder;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InvoicePDFService {

    private final TemplateEngine templateEngine;
    private final SalesOrderRepository salesRepo;
    private final PurchaseOrderRepository purchaseRepo;
    private final CompanyRepository companyRepository;
    private final AuthContext auth;


    public byte[] generateInvoicePdf(Invoice invoice) {

        try {

            SalesOrder salesOrder = null;
            PurchaseOrder purchaseOrder = null;

            Company company = companyRepository.getReferenceById(auth.getCurrentCompanyId());

            if (invoice.getType().name().equals("SALES")) {
                salesOrder = salesRepo.findById(invoice.getOrderId()).orElseThrow(() -> new RuntimeException("no sales order exists"));
            } else {
                purchaseOrder = purchaseRepo.findById(invoice.getOrderId()).orElseThrow(() -> new RuntimeException("no purchase order exists"));
            }

            Context context = new Context();
            context.setVariable("invoice", invoice);
            context.setVariable("company", company);
            context.setVariable("type", invoice.getType());
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
}
