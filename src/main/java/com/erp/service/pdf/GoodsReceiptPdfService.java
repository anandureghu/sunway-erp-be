package com.erp.service.pdf;

import com.erp.domain.purchase.GoodsReceipt;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.service.file.FileStorageService;
import com.erp.util.InMemoryMultipartFile;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class GoodsReceiptPdfService {

    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;

    public byte[] generateGoodsReceiptPdf(GoodsReceipt receipt) {
        try {
            Context context = new Context();
            context.setVariable("receipt", receipt);
            context.setVariable("company", receipt.getCompany());
            context.setVariable("purchaseOrder", receipt.getPurchaseOrder());
            context.setVariable("items", receipt.getItems());

            String html = templateEngine.process("goods_receipt", context);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.useFastMode();
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Goods receipt PDF generation failed", e);
        }
    }

    public String generateAndUploadGoodsReceiptPdf(GoodsReceipt receipt) {
        byte[] pdfBytes = generateGoodsReceiptPdf(receipt);
        InMemoryMultipartFile pdfFile = new InMemoryMultipartFile(
                pdfBytes,
                "GR-" + receipt.getId() + ".pdf",
                "application/pdf"
        );
        FileUploadResult uploadResult = fileStorageService.upload(
                pdfFile,
                FileCategory.GOODS_RECEIPT_PDF,
                receipt.getId().toString(),
                true
        );
        return fileStorageService.getPublicUrl(uploadResult.getBlobPath());
    }
}
