package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.leave.LeavePolicyRequestDTO;
import com.erp.dto.leave.LeavePolicyResponseDTO;
import com.erp.service.LeavePolicyService;
import com.erp.service.security.annotation.HrPermission;
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

    // ======================================================
    // GET ALL LEAVE POLICIES
    // HR_SETTINGS — leave policies are an HR settings operation
    // VIEW_OWN or VIEW_ALL — any HR user can view policies
    // ======================================================
    @HrPermission(module = HrModule.HR_SETTINGS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
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
    @HrPermission(module = HrModule.HR_SETTINGS, action = {HrAction.CREATE})
    @PostMapping("/bulk")
    public ResponseEntity<Void> saveBulkPolicies(
            @PathVariable("companyId") Long companyId,
            @Valid @RequestBody List<LeavePolicyRequestDTO> dtos) {

        service.savePolicies(companyId, dtos);
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // DELETE POLICY
    // HR_SETTINGS — deleting a policy is an admin operation
    // DELETE — requires explicit delete permission
    // ======================================================
    @HrPermission(module = HrModule.HR_SETTINGS, action = {HrAction.DELETE})
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable("companyId") Long companyId,
            @PathVariable("policyId") Long policyId) {

        service.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}