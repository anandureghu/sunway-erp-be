package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.leave.LeavePolicyRequestDTO;
import com.erp.dto.leave.LeavePolicyResponseDTO;
import com.erp.service.LeavePolicyService;
import com.erp.service.hr.QatarLaborLawDefaultsService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/leave-policies")
@RequiredArgsConstructor
public class LeavePolicyController {

    private final LeavePolicyService service;
    private final QatarLaborLawDefaultsService qatarLaborLawDefaultsService;

    // ======================================================
    // GET ALL LEAVE POLICIES
    // HR_SETTINGS — leave policies are an HR settings operation
    // VIEW_OWN or VIEW_ALL — any HR user can view policies
    // ======================================================
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<LeavePolicyResponseDTO>> getAll(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(service.getAllPolicies(companyId));
    }

    // ======================================================
    // BULK SAVE POLICIES
    // HR_SETTINGS — managing leave policies is an admin operation
    // CREATE — bulk saving creates/updates policies
    // ======================================================
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.CREATE})
    @PostMapping("/bulk")
    public ResponseEntity<Void> saveBulkPolicies(
            @PathVariable("companyId") Long companyId,
            @Valid @RequestBody List<LeavePolicyRequestDTO> dtos) {

        service.savePolicies(companyId, dtos);
        return ResponseEntity.ok().build();
    }

    /**
     * Force-apply Qatar statutory leave defaults (Sick/Maternity/Hajj/Marriage/Bereavement)
     * for every role in this company and re-sync employee balances. Does not change
     * Annual / Emergency / Unpaid policies.
     */
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.CREATE})
    @PostMapping("/reset-qatar-defaults")
    public ResponseEntity<List<LeavePolicyResponseDTO>> resetQatarDefaults(
            @PathVariable("companyId") Long companyId) {

        // Tenant guard (getAllPolicies asserts same company / SUPER_ADMIN).
        service.getAllPolicies(companyId);
        qatarLaborLawDefaultsService.applyLeaveDefaults(companyId);
        return ResponseEntity.ok(service.getAllPolicies(companyId));
    }

    // ======================================================
    // DELETE POLICY
    // HR_SETTINGS — deleting a policy is an admin operation
    // DELETE — requires explicit delete permission
    // ======================================================
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.DELETE})
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable("companyId") Long companyId,
            @PathVariable("policyId") Long policyId) {

        service.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}
