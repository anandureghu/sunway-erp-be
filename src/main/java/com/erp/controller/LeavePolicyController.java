package com.erp.controller;

import com.erp.dto.leave.LeavePolicyRequestDTO;
import com.erp.dto.leave.LeavePolicyResponseDTO;
import com.erp.service.LeavePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/leave-policies")
@RequiredArgsConstructor
public class LeavePolicyController {

    private final LeavePolicyService service;

    /* ========= INITIALIZE DEFAULT POLICIES (One-time setup) ========= */
    @PostMapping("/initialize")
    public ResponseEntity<Void> initializeDefaults(
            @PathVariable("companyId") Long companyId) {
        service.initializeDefaultPoliciesForCompany(companyId);
        return ResponseEntity.ok().build();
    }

    /* ========= GET ALL POLICIES ========= */
    @GetMapping
    public ResponseEntity<List<LeavePolicyResponseDTO>> getAll(
            @PathVariable("companyId") Long companyId) {
        return ResponseEntity.ok(service.getAllPolicies(companyId));
    }

    /* ========= CREATE NEW POLICY ========= */
    @PostMapping
    public ResponseEntity<LeavePolicyResponseDTO> create(
            @PathVariable("companyId") Long companyId,
            @RequestBody LeavePolicyRequestDTO dto) {
        return ResponseEntity.ok(service.createPolicy(companyId, dto));
    }

    /* ========= UPDATE POLICY ========= */
    @PutMapping("/{policyId}")
    public ResponseEntity<LeavePolicyResponseDTO> update(
            @PathVariable("companyId") Long companyId,
            @PathVariable("policyId") Long policyId,
            @RequestBody LeavePolicyRequestDTO dto) {
        return ResponseEntity.ok(service.updatePolicy(policyId, dto));
    }

    /* ========= DELETE POLICY ========= */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable("companyId") Long companyId,
            @PathVariable("policyId") Long policyId) {
        service.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}