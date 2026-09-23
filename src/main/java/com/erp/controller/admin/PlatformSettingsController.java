package com.erp.controller.admin;

import com.erp.dto.platform.PlatformSettingsRequest;
import com.erp.dto.platform.PlatformSettingsResponse;
import com.erp.service.platform.PlatformSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/platform-settings")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    public PlatformSettingsController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformSettingsResponse get() {
        return platformSettingsService.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformSettingsResponse update(@Valid @RequestBody PlatformSettingsRequest request) {
        return platformSettingsService.update(request);
    }
}
