package com.erp.service.pdf;

import com.erp.domain.finance.Payment;
import com.erp.domain.hr.Company;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.service.file.FileStorageService;
import com.erp.util.InMemoryMultipartFile;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class VendorPaymentReceiptPdfService {

    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;

    public byte[] generatePdf(
            Payment payment,
            Company company,
            PurchaseOrder purchaseOrder,
            String supplierName,
            String purchaseInvoiceCode
    ) {
        try {
            Context context = new Context();
            context.setVariable("payment", payment);
            context.setVariable("company", company);
            context.setVariable("purchaseOrder", purchaseOrder);
            context.setVariable("supplierName", supplierName != null ? supplierName : "—");
            context.setVariable("purchaseInvoiceCode", purchaseInvoiceCode);

            String html = templateEngine.process("vendor_payment_receipt", context);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Vendor payment receipt PDF generation failed", e);
        }
    }

    public String generateAndUpload(
            Payment payment,
            Company company,
            PurchaseOrder purchaseOrder,
            String supplierName,
            String purchaseInvoiceCode
    ) {
        byte[] pdfBytes = generatePdf(payment, company, purchaseOrder, supplierName, purchaseInvoiceCode);
        MultipartFile pdfFile = new InMemoryMultipartFile(
                pdfBytes,
                payment.getPaymentCode() + "-receipt.pdf",
                "application/pdf"
        );
        FileUploadResult uploadResult = fileStorageService.upload(
                pdfFile,
                FileCategory.PAYMENT_RECEIPT_PDF,
                payment.getId().toString(),
                true
        );
        return fileStorageService.getPublicUrl(uploadResult.getBlobPath());
    }
}
