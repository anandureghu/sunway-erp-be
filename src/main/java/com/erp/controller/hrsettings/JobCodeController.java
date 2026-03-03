package com.erp.controller.hrsettings;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.hrsettings.JobCodeRequestDTO;
import com.erp.dto.hrsettings.JobCodeResponseDTO;
import com.erp.service.hrsettings.JobCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hr/job-codes")
@RequiredArgsConstructor
public class JobCodeController {

    private final JobCodeService service;

    /**
     * CREATE Job Code
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.HrModule).HR_SETTINGS, T(com.erp.domain.security.HrAction).CREATE)")
    @PostMapping
    public JobCodeResponseDTO create(
            @Valid @RequestBody JobCodeRequestDTO dto) {
        return service.create(dto);
    }

    /**
     * GET All Job Codes
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.HrModule).HR_SETTINGS, T(com.erp.domain.security.HrAction).VIEW_ALL)")
    @GetMapping
    public List<JobCodeResponseDTO> getAll() {
        return service.getAll();
    }

    /**
     * GET Active Job Codes
     * Requires either VIEW_OWN or VIEW_ALL
     */
    @PreAuthorize("""
        @permissionChecker.has(authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_OWN)
        or
        @permissionChecker.has(authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_ALL)
    """)
    @GetMapping("/active")
    public List<JobCodeResponseDTO> getActive() {
        return service.getActive();
    }

    /**
     * UPDATE Job Code
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.HrModule).HR_SETTINGS, T(com.erp.domain.security.HrAction).EDIT)")
    @PutMapping("/{id}")
    public JobCodeResponseDTO update(
            @PathVariable("id") Long id,
            @Valid @RequestBody JobCodeRequestDTO dto) {
        return service.update(id, dto);
    }

    /**
     * DELETE Job Code
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.HrModule).HR_SETTINGS, T(com.erp.domain.security.HrAction).DELETE)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}