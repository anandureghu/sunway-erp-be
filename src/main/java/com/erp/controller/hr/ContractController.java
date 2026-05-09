package com.erp.controller.hr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.dto.hr.ContractRequestDTO;
import com.erp.dto.hr.ContractResponseDTO;
import com.erp.service.common.ContractService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hr/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

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

    @PostMapping(value = "/employee/{employeeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponseDTO> createContractMultipart(
            @PathVariable("employeeId") Long employeeId,
            @RequestPart("data") String requestJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        ContractRequestDTO request = parseAndValidate(requestJson);

        ContractResponseDTO response =
                contractService.createContract(employeeId, request, attachment);

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

    @PutMapping(value = "/{contractId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponseDTO> updateContractMultipart(
            @PathVariable("contractId") Long contractId,
            @RequestPart("data") String requestJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        ContractRequestDTO request = parseAndValidate(requestJson);

        ContractResponseDTO response =
                contractService.updateContract(contractId, request, attachment);

        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/{contractId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponseDTO> uploadContractAttachment(
            @PathVariable("contractId") Long contractId,
            @RequestPart("attachment") MultipartFile attachment
    ) {
        return ResponseEntity.ok(contractService.updateContractAttachment(contractId, attachment));
    }

    @PutMapping(value = "/employee/{employeeId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponseDTO> uploadEmployeeContractAttachment(
            @PathVariable("employeeId") Long employeeId,
            @RequestPart("attachment") MultipartFile attachment
    ) {
        return ResponseEntity.ok(contractService.updateEmployeeContractAttachment(employeeId, attachment));
    }

    // ====================================================
    // GET CONTRACT BY EMPLOYEE
    // ====================================================

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ContractResponseDTO> getByEmployee(
            @PathVariable("employeeId") Long employeeId
    ) {

        ContractResponseDTO contract =
                contractService.getByEmployee(employeeId);

        if (contract == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(contract);
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

    private ContractRequestDTO parseAndValidate(String requestJson) {
        try {
            ContractRequestDTO request = objectMapper.readValue(requestJson, ContractRequestDTO.class);
            Set<ConstraintViolation<ContractRequestDTO>> violations = validator.validate(request);

            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .collect(Collectors.joining(", "));
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            }

            return request;
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contract data", e);
        }
    }
}
