package com.erp.controller.hr;

import com.erp.dto.hr.CompanyRoleDTO;
import com.erp.service.hr.CompanyRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class CompanyRoleController {

    private final CompanyRoleService roleService;

    /* ── GET /api/roles?companyId=2 ── */
    @GetMapping
    public ResponseEntity<List<CompanyRoleDTO.Response>> list(
            @RequestParam("companyId") Long companyId
    ) {
        return ResponseEntity.ok(roleService.listByCompany(companyId));
    }

    /* ── GET /api/roles/active?companyId=2 ── */
    @GetMapping("/active")
    public ResponseEntity<List<CompanyRoleDTO.Response>> listActive(
            @RequestParam("companyId") Long companyId
    ) {
        return ResponseEntity.ok(roleService.listActiveByCompany(companyId));
    }

    /* ── GET /api/roles/{id} ── */
    @GetMapping("/{id}")
    public ResponseEntity<CompanyRoleDTO.Response> getById(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    /* ── POST /api/roles ── */
    @PostMapping
    public ResponseEntity<CompanyRoleDTO.Response> create(
            @Valid @RequestBody CompanyRoleDTO.Request dto
    ) {
        return ResponseEntity.ok(roleService.create(dto));
    }

    /* ── PUT /api/roles/{id} ── */
    @PutMapping("/{id}")
    public ResponseEntity<CompanyRoleDTO.Response> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompanyRoleDTO.Request dto
    ) {
        return ResponseEntity.ok(roleService.update(id, dto));
    }

    /* ── PUT /api/roles/{id}/toggle ── */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<CompanyRoleDTO.Response> toggle(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(roleService.toggleActive(id));
    }

    /* ── DELETE /api/roles/{id} ── */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id
    ) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}