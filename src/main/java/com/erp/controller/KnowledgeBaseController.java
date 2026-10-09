package com.erp.controller;

import com.erp.dto.admin.KnowledgeBaseItemResponse;
import com.erp.service.admin.KnowledgeBaseService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<KnowledgeBaseItemResponse> list() {
        return knowledgeBaseService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public KnowledgeBaseItemResponse get(@PathVariable Long id) {
        return knowledgeBaseService.getById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public KnowledgeBaseItemResponse upload(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart("file") MultipartFile file
    ) {
        return knowledgeBaseService.upload(title, description, file);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
    }
}
