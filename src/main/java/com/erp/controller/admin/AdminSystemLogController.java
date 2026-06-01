package com.erp.controller.admin;

import com.erp.dto.admin.AdminSystemLogResponseDTO;
import com.erp.dto.common.PagedResponse;
import com.erp.service.admin.AdminSystemLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/system-logs")
public class AdminSystemLogController {

    private final AdminSystemLogService adminSystemLogService;

    public AdminSystemLogController(AdminSystemLogService adminSystemLogService) {
        this.adminSystemLogService = adminSystemLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PagedResponse<AdminSystemLogResponseDTO> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
                adminSystemLogService.list(level, module, search, userId, from, to, pageable)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminSystemLogResponseDTO getById(@PathVariable Long id) {
        return adminSystemLogService.getById(id);
    }
}
