package com.erp.service.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class FileStorageService {

    private final String basePath = "uploads/invoices/";

    public String savePdf(String invoiceId, byte[] pdfBytes) {

        try {
            File folder = new File(basePath);
            if (!folder.exists()) folder.mkdirs();

            String filePath = basePath + invoiceId + ".pdf";

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfBytes);
            }

            // Return URL like http://localhost:8080/uploads/invoices/file.pdf
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/invoices/")
                    .path(invoiceId + ".pdf")
                    .toUriString();

        } catch (IOException ex) {
            throw new RuntimeException("Failed to save invoice PDF", ex);
        }
    }
}
