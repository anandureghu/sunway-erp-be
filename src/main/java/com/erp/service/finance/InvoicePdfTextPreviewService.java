package com.erp.service.finance;

import com.erp.dto.finance.InvoicePdfTextPreviewResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class InvoicePdfTextPreviewService {

    private static final int MAX_CHARS = 8000;

    public InvoicePdfTextPreviewResponse extractPreview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }
        try {
            byte[] bytes = file.getBytes();
            try (PDDocument doc = PDDocument.load(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int pages = doc.getNumberOfPages();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(3, Math.max(1, pages)));
                String text = stripper.getText(doc);
                if (text == null) {
                    text = "";
                }
                text = text.trim();
                if (text.length() > MAX_CHARS) {
                    text = text.substring(0, MAX_CHARS) + "\n…";
                }
                return new InvoicePdfTextPreviewResponse(text);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PDF text", e);
        }
    }
}
