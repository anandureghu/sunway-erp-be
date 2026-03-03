package com.erp.service.common;

import com.erp.domain.Employee;
import com.erp.domain.hr.*;
import com.erp.dto.hr.AllowanceRequestDTO;
import com.erp.dto.hr.AllowanceResponseDTO;
import com.erp.dto.hr.ContractRequestDTO;
import com.erp.dto.hr.ContractResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.AllowanceTypeRepository;
import com.erp.repo.hr.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final AllowanceTypeRepository allowanceTypeRepository;
    private final CodeGeneratorService codeGeneratorService;

    // ================= CREATE =================

    public ContractResponseDTO createContract(Long employeeId,
                                              ContractRequestDTO dto) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Allow only one active contract per employee
        contractRepository
                .findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(employeeId)
                .ifPresent(existing -> existing.setDeleted(true));

        Contract contract = Contract.builder()
                .contractCode(codeGeneratorService.generateContractCode())
                .contractType(dto.getContractType())
                .status(dto.getStatus())
                .effectiveDate(dto.getEffectiveDate())
                .expirationDate(dto.getExpirationDate())
                .contractPeriodMonths(dto.getContractPeriodMonths())
                .noticePeriodDays(dto.getNoticePeriodDays())
                .salaryRateType(dto.getSalaryRateType())
                .signatureDate(dto.getSignatureDate())
                .signedBy(dto.getSignedBy())
                .attachmentPath(dto.getAttachmentUrl())
                .termsAndConditions(dto.getTermsAndConditions())
                .employee(employee)
                .allowances(new ArrayList<>())
                .build();

        mapAllowances(contract, dto.getAllowances());

        Contract saved = contractRepository.save(contract);

        return mapToResponse(saved);
    }

    // ================= UPDATE =================

    public ContractResponseDTO updateContract(Long contractId,
                                              ContractRequestDTO dto) {

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.isDeleted()) {
            throw new RuntimeException("Cannot update deleted contract");
        }

        contract.setContractType(dto.getContractType());
        contract.setStatus(dto.getStatus());
        contract.setEffectiveDate(dto.getEffectiveDate());
        contract.setExpirationDate(dto.getExpirationDate());
        contract.setContractPeriodMonths(dto.getContractPeriodMonths());
        contract.setNoticePeriodDays(dto.getNoticePeriodDays());
        contract.setSalaryRateType(dto.getSalaryRateType());
        contract.setSignatureDate(dto.getSignatureDate());
        contract.setSignedBy(dto.getSignedBy());
        contract.setAttachmentPath(dto.getAttachmentUrl());
        contract.setTermsAndConditions(dto.getTermsAndConditions());

        // Replace allowance package
        contract.getAllowances().clear();

        mapAllowances(contract, dto.getAllowances());

        return mapToResponse(contract);
    }

    // ================= GET =================

    @Transactional(readOnly = true)
    public ContractResponseDTO getByEmployee(Long employeeId) {

        return contractRepository
                .findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(employeeId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    // ================= DELETE =================

    public void delete(Long contractId) {

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        contract.setDeleted(true);
    }

    // ================= ALLOWANCE MAPPING =================

    private void mapAllowances(Contract contract,
                               List<AllowanceRequestDTO> allowanceDTOs) {

        if (allowanceDTOs == null || allowanceDTOs.isEmpty()) {
            return;
        }

        for (AllowanceRequestDTO dto : allowanceDTOs) {

            if (dto.getAllowanceTypeId() == null) {
                throw new RuntimeException("Allowance type is required");
            }

            AllowanceType type = allowanceTypeRepository
                    .findById(dto.getAllowanceTypeId())
                    .orElseThrow(() ->
                            new RuntimeException("Allowance type not found: " + dto.getAllowanceTypeId())
                    );

            SalaryAllowance allowance = SalaryAllowance.builder()
                    .allowanceType(type)
                    .amount(dto.getAmount())
                    .effectiveDate(dto.getEffectiveDate())
                    .note(dto.getNote())
                    .contract(contract)
                    .build();

            contract.getAllowances().add(allowance);
        }
    }

    // ================= RESPONSE MAPPING =================

    private ContractResponseDTO mapToResponse(Contract contract) {

        List<AllowanceResponseDTO> allowanceResponses =
                contract.getAllowances()
                        .stream()
                        .map(a -> AllowanceResponseDTO.builder()
                                .id(a.getId())
                                .allowanceTypeId(a.getAllowanceType().getId())
                                .allowanceType(a.getAllowanceType().getName())
                                .amount(a.getAmount())
                                .effectiveDate(a.getEffectiveDate())
                                .note(a.getNote())
                                .build())
                        .toList();

        return ContractResponseDTO.builder()
                .id(contract.getId())
                .contractCode(contract.getContractCode())
                .contractType(contract.getContractType())
                .status(contract.getStatus())
                .effectiveDate(contract.getEffectiveDate())
                .expirationDate(contract.getExpirationDate())
                .contractPeriodMonths(contract.getContractPeriodMonths())
                .noticePeriodDays(contract.getNoticePeriodDays())
                .salaryRateType(contract.getSalaryRateType())
                .signatureDate(contract.getSignatureDate())
                .signedBy(contract.getSignedBy())
                .termsAndConditions(contract.getTermsAndConditions())
                .attachmentUrl(contract.getAttachmentPath())
                .employeeId(contract.getEmployee().getId())
                .staffName(
                        contract.getEmployee().getFirstName() + " " +
                                contract.getEmployee().getLastName()
                )
                .allowances(allowanceResponses)
                .build();
    }
}