package com.erp.controller.admin;

import com.erp.domain.admin.EnquiryStatus;
import com.erp.dto.admin.EnquiryResponseDTO;
import com.erp.dto.admin.EnquiryUpdateRequest;
import com.erp.dto.common.PagedResponse;
import com.erp.service.admin.EnquiryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/enquiries")
public class AdminEnquiryController {

    private final EnquiryService enquiryService;

    public AdminEnquiryController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PagedResponse<EnquiryResponseDTO> list(
            @RequestParam(required = false) EnquiryStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(enquiryService.list(status, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EnquiryResponseDTO getById(@PathVariable Long id) {
        return enquiryService.getById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EnquiryResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody EnquiryUpdateRequest request
    ) {
        return enquiryService.update(id, request);
    }
}
