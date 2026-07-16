package com.erp.service.file;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.erp.domain.hr.StoredFile;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.repo.hr.StoredFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {

    private final BlobServiceClient blobServiceClient;
    private final StoredFileRepository storedFileRepository;

    @Value("${azure.storage.public-container}")
    private String publicContainer;

    @Value("${azure.storage.private-container}")
    private String privateContainer;

    public FileStorageService(
            @Value("${azure.storage.connection-string}") String connectionString,
            StoredFileRepository storedFileRepository
    ) {
        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        this.storedFileRepository = storedFileRepository;
    }

    // ======================================================
    // Upload (call ONLY after entity exists)
    // ======================================================
    public FileUploadResult upload(
            MultipartFile file,
            FileCategory category,
            String entityId,
            boolean isPublic,
            Long companyId
    ) {

        validateFile(file, category);

        String containerName = isPublic ? publicContainer : privateContainer;
        BlobContainerClient container =
                blobServiceClient.getBlobContainerClient(containerName);

        String extension = getExtension(
                Objects.requireNonNull(file.getOriginalFilename())
        );

        String blobPath = buildPath(category, entityId, extension);
        BlobClient blobClient = container.getBlobClient(blobPath);

        try (InputStream inputStream = file.getInputStream()) {

            blobClient.upload(inputStream, file.getSize(), true);

            blobClient.setHttpHeaders(
                    new BlobHttpHeaders().setContentType(file.getContentType())
            );

            recordStoredFile(companyId, blobPath, containerName, file.getSize());

            return new FileUploadResult(blobPath, isPublic);

        } catch (IOException e) {
            throw new RuntimeException("File upload failed", e);
        }
    }

    /** Upserts the ledger row for this blob path so per-company cloud storage totals stay accurate,
     *  including when a fixed-name blob (e.g. a profile photo) is overwritten by a re-upload. */
    private void recordStoredFile(Long companyId, String blobPath, String containerName, long sizeBytes) {
        StoredFile storedFile = storedFileRepository.findByBlobPath(blobPath)
                .orElseGet(() -> StoredFile.builder().blobPath(blobPath).build());
        storedFile.setCompanyId(companyId);
        storedFile.setContainer(containerName);
        storedFile.setSizeBytes(sizeBytes);
        storedFile.setUpdatedAt(Instant.now());
        storedFileRepository.save(storedFile);
    }

    // ======================================================
    // URL Builders
    // ======================================================

    public String buildPublicUrl(String blobPath) {
        BlobContainerClient container =
                blobServiceClient.getBlobContainerClient(publicContainer);
        return container.getBlobContainerUrl() + "/" + blobPath;
    }

    public String buildPrivateSasUrl(String blobPath, int expiryMinutes) {

        BlobContainerClient container =
                blobServiceClient.getBlobContainerClient(privateContainer);

        BlobClient blobClient = container.getBlobClient(blobPath);

        BlobSasPermission permission = new BlobSasPermission()
                .setReadPermission(true);

        OffsetDateTime expiry =
                OffsetDateTime.now().plusMinutes(expiryMinutes);

        BlobServiceSasSignatureValues values =
                new BlobServiceSasSignatureValues(expiry, permission);

        String sas = blobClient.generateSas(values);
        return blobClient.getBlobUrl() + "?" + sas;
    }

    // ======================================================
    // Helpers
    // ======================================================

    private void validateFile(MultipartFile file, FileCategory category) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        switch (category) {

            case EMPLOYEE_PROFILE, INVENTORY_IMAGE, COMPANY_LOGO -> {
                if (!Objects.requireNonNull(file.getContentType())
                        .startsWith("image/")) {
                    throw new IllegalArgumentException("Only images allowed");
                }
                if (file.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("Image size > 5MB");
                }
            }

            case INVOICE_PDF, GOODS_RECEIPT_PDF -> {
                if (!Objects.equals(
                        file.getContentType(), "application/pdf")) {
                    throw new IllegalArgumentException("Only PDF allowed");
                }
                if (file.getSize() > 15 * 1024 * 1024) {
                    throw new IllegalArgumentException("PDF size > 15MB");
                }
            }

            case LEAVE_SUPPORTING_DOCUMENT -> {
                String contentType = Objects.requireNonNull(file.getContentType());
                boolean isPdf = Objects.equals(contentType, "application/pdf");
                boolean isImage = contentType.startsWith("image/");

                if (!isPdf && !isImage) {
                    throw new IllegalArgumentException("Only PDF, JPG, JPEG, or PNG allowed");
                }
                if (file.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("Supporting document size > 5MB");
                }
            }

            case VENDOR_INVOICE_MATCH_DOCUMENT -> {
                String contentType = Objects.requireNonNull(file.getContentType());
                boolean isPdf = Objects.equals(contentType, "application/pdf");
                boolean isImage = contentType.startsWith("image/");

                if (!isPdf && !isImage) {
                    throw new IllegalArgumentException("Only PDF, JPG, JPEG, or PNG allowed");
                }
                if (file.getSize() > 15 * 1024 * 1024) {
                    throw new IllegalArgumentException("Vendor invoice document size > 15MB");
                }
            }

            case CONTRACT_ATTACHMENT, PURCHASE_REQUISITION_DOCUMENT -> {
                String contentType = Objects.requireNonNull(file.getContentType());
                boolean isPdf = Objects.equals(contentType, "application/pdf");
                boolean isWord = Objects.equals(contentType, "application/msword")
                        || Objects.equals(contentType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                boolean isImage = contentType.startsWith("image/");

                if (!isPdf && !isWord && !isImage) {
                    throw new IllegalArgumentException("Only PDF, DOC, DOCX, JPG, JPEG, or PNG allowed");
                }
                if (file.getSize() > 15 * 1024 * 1024) {
                    throw new IllegalArgumentException("File size > 15MB");
                }
            }
        }
    }

    private String buildPath(
            FileCategory category,
            String entityId,
            String extension
    ) {
        return switch (category) {

            case EMPLOYEE_PROFILE -> "employees/" + entityId + "/profile." + extension;

            case INVENTORY_IMAGE -> "inventory/" + entityId + "/"
                    + UUID.randomUUID() + "." + extension;

            case COMPANY_LOGO -> "companies/" + entityId + "/logo." + extension;

            case INVOICE_PDF -> "invoices/" + entityId + "/"
                    + UUID.randomUUID() + ".pdf";

            case PAYMENT_RECEIPT_PDF -> "payments/" + entityId + "/"
                    + UUID.randomUUID() + ".pdf";

            case GOODS_RECEIPT_PDF -> "goods-receipts/" + entityId + "/"
                    + UUID.randomUUID() + ".pdf";

            case LEAVE_SUPPORTING_DOCUMENT -> "leaves/" + entityId + "/supporting-document." + extension;

            case VENDOR_INVOICE_MATCH_DOCUMENT -> "invoices/" + entityId + "/vendor-match-"
                    + UUID.randomUUID() + "." + extension;

            case CONTRACT_ATTACHMENT -> "contracts/" + entityId + "/attachment." + extension;

            case PURCHASE_REQUISITION_DOCUMENT -> "purchase-requisitions/" + entityId + "/"
                    + UUID.randomUUID() + "." + extension;

            case PASSPORT_DOCUMENT -> "immigration/" + entityId + "/passport/"
                    + UUID.randomUUID() + "." + extension;

            case RESIDENCE_PERMIT_DOCUMENT -> "immigration/" + entityId + "/residence-permit/"
                    + UUID.randomUUID() + "." + extension;
        };
    }

    private String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    public String getPublicUrl(String blobPath) {
        if (blobPath == null || blobPath.isBlank()) {
            return null;
        }
        BlobContainerClient container =
                blobServiceClient.getBlobContainerClient(publicContainer);
        return container.getBlobContainerUrl() + "/" + blobPath;
    }

    public String getPrivateSasUrl(String blobPath) {

        BlobContainerClient container =
                blobServiceClient.getBlobContainerClient(privateContainer);

        BlobClient blobClient = container.getBlobClient(blobPath);

        BlobSasPermission permission = new BlobSasPermission()
                .setReadPermission(true);

        OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(10);

        BlobServiceSasSignatureValues values =
                new BlobServiceSasSignatureValues(expiry, permission);

        return blobClient.getBlobUrl() + "?" + blobClient.generateSas(values);
    }
}
