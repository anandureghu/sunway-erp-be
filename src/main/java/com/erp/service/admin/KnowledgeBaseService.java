package com.erp.service.admin;

import com.erp.domain.admin.KnowledgeBaseContentType;
import com.erp.domain.admin.KnowledgeBaseItem;
import com.erp.dto.admin.KnowledgeBaseItemResponse;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.exception.NotFoundException;
import com.erp.repo.admin.KnowledgeBaseItemRepository;
import com.erp.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseItemRepository repository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<KnowledgeBaseItemResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseItemResponse getById(Long id) {
        KnowledgeBaseItem item = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Knowledge base item not found"));
        return toDto(item);
    }

    @Transactional
    public KnowledgeBaseItemResponse upload(String title, String description, MultipartFile file) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("Title is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        KnowledgeBaseContentType contentType = resolveContentType(file);
        KnowledgeBaseItem item = KnowledgeBaseItem.builder()
                .title(title.trim())
                .description(StringUtils.hasText(description) ? description.trim() : null)
                .contentType(contentType)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                .contentTypeMime(file.getContentType())
                .sizeBytes(file.getSize())
                .blobPath("pending")
                .build();
        item = repository.save(item);

        FileUploadResult upload = fileStorageService.upload(
                file,
                FileCategory.KNOWLEDGE_BASE,
                String.valueOf(item.getId()),
                true,
                null
        );
        item.setBlobPath(upload.getBlobPath());
        item = repository.save(item);
        return toDto(item);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBaseItem item = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Knowledge base item not found"));
        String blobPath = item.getBlobPath();
        repository.delete(item);
        if (StringUtils.hasText(blobPath) && !"pending".equals(blobPath)) {
            fileStorageService.deleteByBlobPath(blobPath);
        }
    }

    private KnowledgeBaseContentType resolveContentType(MultipartFile file) {
        String mime = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        if (mime.startsWith("video/")
                || name.endsWith(".mp4")
                || name.endsWith(".webm")
                || name.endsWith(".mov")
                || name.endsWith(".mkv")) {
            return KnowledgeBaseContentType.VIDEO;
        }
        return KnowledgeBaseContentType.DOCUMENT;
    }

    private KnowledgeBaseItemResponse toDto(KnowledgeBaseItem item) {
        return KnowledgeBaseItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .contentType(item.getContentType())
                .fileName(item.getFileName())
                .contentTypeMime(item.getContentTypeMime())
                .sizeBytes(item.getSizeBytes())
                .fileUrl(fileStorageService.getPublicUrl(item.getBlobPath()))
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
