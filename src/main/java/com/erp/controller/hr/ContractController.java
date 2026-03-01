package com.erp.controller.hr;

import com.erp.dto.hr.ContractRequestDTO;
import com.erp.dto.hr.ContractResponseDTO;
import com.erp.service.common.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    // ====================================================
    // CREATE CONTRACT
    // ====================================================

    @PostMapping("/employee/{employeeId}")
    public ResponseEntity<ContractResponseDTO> createContract(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody ContractRequestDTO request
    ) {

        ContractResponseDTO response =
                contractService.createContract(employeeId, request);

        return ResponseEntity.ok(response);
    }

    // ====================================================
    // UPDATE CONTRACT
    // ====================================================

    @PutMapping("/{contractId}")
    public ResponseEntity<ContractResponseDTO> updateContract(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ContractRequestDTO request
    ) {

        ContractResponseDTO response =
                contractService.updateContract(contractId, request);

        return ResponseEntity.ok(response);
    }

    // ====================================================
    // GET CONTRACT BY EMPLOYEE
    // ====================================================

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ContractResponseDTO> getByEmployee(
            @PathVariable("employeeId") Long employeeId
    ) {

        ContractResponseDTO response =
                contractService.getByEmployee(employeeId);

        return ResponseEntity.ok(response);
    }

    // ====================================================
    // DELETE CONTRACT (Soft Delete)
    // ====================================================

    @DeleteMapping("/{contractId}")
    public ResponseEntity<Void> deleteContract(
            @PathVariable("contractId") Long contractId
    ) {

        contractService.delete(contractId);

        return ResponseEntity.noContent().build();
    }
}