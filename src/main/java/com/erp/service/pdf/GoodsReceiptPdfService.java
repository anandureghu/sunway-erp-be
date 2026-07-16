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
            var items = receipt.getItems() != null ? receipt.getItems() : java.util.List.<com.erp.domain.purchase.GoodsReceiptItem>of();
            int totalAccepted = items.stream()
                    .mapToInt(i -> i.getAcceptedQty() != null ? i.getAcceptedQty() : 0)
                    .sum();
            int totalRejected = items.stream()
                    .mapToInt(i -> i.getRejectedQty() != null ? i.getRejectedQty() : 0)
                    .sum();
            int totalReceived = items.stream()
                    .mapToInt(i -> i.getReceivedQty() != null ? i.getReceivedQty() : 0)
                    .sum();

            // Touch lazy associations while the persistence context is open.
            if (receipt.getCompany() != null) {
                receipt.getCompany().getCompanyName();
            }
            if (receipt.getReceivedBy() != null) {
                receipt.getReceivedBy().getFullName();
            }
            if (receipt.getInspectedBy() != null) {
                receipt.getInspectedBy().getFullName();
            }
            if (receipt.getAuthorizedBy() != null) {
                receipt.getAuthorizedBy().getFullName();
            }
            var po = receipt.getPurchaseOrder();
            if (po != null) {
                po.getOrderNumber();
                if (po.getSupplier() != null) {
                    po.getSupplier().getVendorName();
                }
                if (po.getRequestedBy() != null) {
                    po.getRequestedBy().getFullName();
                }
                if (po.getSourceRequisition() != null) {
                    po.getSourceRequisition().getRequisitionNumber();
                    if (po.getSourceRequisition().getRequestedBy() != null) {
                        po.getSourceRequisition().getRequestedBy().getFullName();
                    }
                }
            }
            for (var line : items) {
                if (line.getItem() != null) {
                    line.getItem().getName();
                    line.getItem().getSku();
                    if (line.getItem().getWarehouse() != null) {
                        line.getItem().getWarehouse().getName();
                    }
                }
                if (line.getWarehouse() != null) {
                    line.getWarehouse().getName();
                }
            }

            java.time.format.DateTimeFormatter dateTimeFmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")
                            .withZone(java.time.ZoneId.systemDefault());
            java.time.format.DateTimeFormatter dateFmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy");

            String receivedAtFormatted = receipt.getReceivedAt() != null
                    ? dateTimeFmt.format(receipt.getReceivedAt())
                    : "—";
            String requiredDeliveryFormatted = "—";
            if (po != null && po.getRequiredDeliveryDate() != null) {
                requiredDeliveryFormatted = po.getRequiredDeliveryDate().format(dateFmt);
            } else if (po != null
                    && po.getSourceRequisition() != null
                    && po.getSourceRequisition().getRequiredDeliveryDate() != null) {
                requiredDeliveryFormatted =
                        po.getSourceRequisition().getRequiredDeliveryDate().format(dateFmt);
            }

            Context context = new Context();
            context.setVariable("receipt", receipt);
            context.setVariable("company", receipt.getCompany());
            context.setVariable("purchaseOrder", po);
            context.setVariable("items", items);
            context.setVariable("totalLines", items.size());
            context.setVariable("totalReceived", totalReceived);
            context.setVariable("totalAccepted", totalAccepted);
            context.setVariable("totalRejected", totalRejected);
            context.setVariable("receivedAtFormatted", receivedAtFormatted);
            context.setVariable("requiredDeliveryFormatted", requiredDeliveryFormatted);

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
                true,
                receipt.getCompany().getId()
        );
        return fileStorageService.getPublicUrl(uploadResult.getBlobPath());
    }
}
