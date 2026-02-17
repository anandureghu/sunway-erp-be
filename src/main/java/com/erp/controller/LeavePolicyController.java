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

    /* =====================================================
       GET ALL POLICIES FOR COMPANY
    ===================================================== */
    @GetMapping
    public ResponseEntity<List<LeavePolicyResponseDTO>> getAll(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(service.getAllPolicies(companyId));
    }

    /* =====================================================
       BULK SAVE POLICIES
    ===================================================== */
    @PostMapping("/bulk")
    public ResponseEntity<Void> saveBulkPolicies(
            @PathVariable("companyId") Long companyId,
            @RequestBody List<LeavePolicyRequestDTO> dtos) {

        service.savePolicies(companyId, dtos);
        return ResponseEntity.ok().build();
    }

    /* =====================================================
       DELETE POLICY
    ===================================================== */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable("companyId") Long companyId,
            @PathVariable("policyId") Long policyId) {

        service.deletePolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}
