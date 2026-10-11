package com.erp.dto.admin;

import com.erp.domain.admin.KnowledgeBaseContentType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class KnowledgeBaseItemResponse {
    Long id;
    String title;
    String description;
    KnowledgeBaseContentType contentType;
    String fileName;
    String contentTypeMime;
    long sizeBytes;
    String fileUrl;
    Instant createdAt;
    Instant updatedAt;
}
