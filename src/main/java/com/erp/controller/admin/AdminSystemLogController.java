package com.erp.controller.admin;

import com.erp.dto.admin.AdminSystemLogResponseDTO;
import com.erp.dto.common.PagedResponse;
import com.erp.service.admin.AdminSystemLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable
    ) {
        return PagedResponse.from(adminSystemLogService.list(level, module, pageable));
    }
}
