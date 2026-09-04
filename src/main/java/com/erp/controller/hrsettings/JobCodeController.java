package com.erp.controller.hrsettings;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
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
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).CREATE)")
    @PostMapping
    public JobCodeResponseDTO create(
            @Valid @RequestBody JobCodeRequestDTO dto) {
        return service.create(dto);
    }

    /**
     * GET All Job Codes
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).VIEW_ALL)")
    @GetMapping
    public List<JobCodeResponseDTO> getAll() {
        return service.getAll();
    }

    /**
     * GET Active Job Codes.
     *
     * Active job codes are reference data (the designation/title list) that an
     * employee needs to view or set their own Current Job and Profile. Allow
     * anyone who can view HR Settings, their Current Job, or their Employee
     * Profile (own or all) — not just HR Settings managers.
     */
    @PreAuthorize("""
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).HR_SETTINGS,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
        or
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).CURRENT_JOB,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
        or
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).EMPLOYEE_PROFILE,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
    """)
    @GetMapping("/active")
    public List<JobCodeResponseDTO> getActive() {
        return service.getActive();
    }

    /**
     * GET Assignable Job Codes for an employee — the active list minus codes already
     * held by another still-employed person (a code frees up when its holder exits).
     * Used to populate the Current Job job-code dropdown so a taken code can't be picked.
     */
    @PreAuthorize("""
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).HR_SETTINGS,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
        or
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).CURRENT_JOB,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
        or
        @permissionChecker.hasAny(authentication,
            T(com.erp.domain.security.AppModule).EMPLOYEE_PROFILE,
            T(com.erp.domain.security.AppAction).VIEW_OWN,
            T(com.erp.domain.security.AppAction).VIEW_ALL)
    """)
    @GetMapping("/assignable")
    public List<JobCodeResponseDTO> getAssignable(
            @RequestParam(name = "employeeId", required = false) Long employeeId) {
        return service.getAssignable(employeeId);
    }

    /**
     * UPDATE Job Code
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).EDIT)")
    @PutMapping("/{id}")
    public JobCodeResponseDTO update(
            @PathVariable("id") Long id,
            @Valid @RequestBody JobCodeRequestDTO dto) {
        return service.update(id, dto);
    }

    /**
     * APPROVE a pending job code (HR manager).
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).EDIT)")
    @PutMapping("/{id}/approve")
    public JobCodeResponseDTO approve(@PathVariable("id") Long id) {
        return service.decide(id, true);
    }

    /**
     * REJECT a pending job code (HR manager).
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).EDIT)")
    @PutMapping("/{id}/reject")
    public JobCodeResponseDTO reject(@PathVariable("id") Long id) {
        return service.decide(id, false);
    }

    /**
     * DELETE Job Code
     */
    @PreAuthorize("@permissionChecker.has(authentication, T(com.erp.domain.security.AppModule).HR_SETTINGS, T(com.erp.domain.security.AppAction).DELETE)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}