package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionDocumentDTO {

    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSizeBytes;
    private String downloadUrl;
    private Instant uploadedAt;
    private Long uploadedById;
    private String uploadedByName;
}
