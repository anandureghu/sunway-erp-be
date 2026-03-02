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

        Contract contract = new Contract();
        contract.setContractCode(codeGeneratorService.generateContractCode());
        contract.setContractType(dto.getContractType());
        contract.setStatus(dto.getStatus());
        contract.setEffectiveDate(dto.getEffectiveDate());
        contract.setExpirationDate(dto.getExpirationDate());
        contract.setNoticePeriodDays(dto.getNoticePeriodDays());
        contract.setEmployee(employee);
        contract.setAllowances(new ArrayList<>());

        mapAllowances(contract, dto.getAllowances());

        Contract saved = contractRepository.save(contract);

        return mapToResponse(saved);
    }

    // ================= UPDATE =================

    public ContractResponseDTO updateContract(Long contractId,
                                              ContractRequestDTO dto) {

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        contract.setContractType(dto.getContractType());
        contract.setStatus(dto.getStatus());
        contract.setEffectiveDate(dto.getEffectiveDate());
        contract.setExpirationDate(dto.getExpirationDate());
        contract.setNoticePeriodDays(dto.getNoticePeriodDays());

        // Clear old allowances (requires orphanRemoval = true)
        contract.getAllowances().clear();

        mapAllowances(contract, dto.getAllowances());

        return mapToResponse(contract);
    }

    // ================= GET =================

    @Transactional(readOnly = true)
    public ContractResponseDTO getByEmployee(Long employeeId) {

        return contractRepository.findByEmployeeId(employeeId)
                .map(this::mapToResponse)
                .orElse(null);   // IMPORTANT: no exception, no 500
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

            String name = dto.getAllowanceType().trim();

            // Find existing OR create new automatically
            AllowanceType type = allowanceTypeRepository
                    .findByNameIgnoreCase(name)
                    .orElseGet(() -> {
                        AllowanceType newType = new AllowanceType();
                        newType.setName(name);
                        newType.setActive(true);
                        return allowanceTypeRepository.save(newType);
                    });

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
                .noticePeriodDays(contract.getNoticePeriodDays())
                .employeeId(contract.getEmployee().getId())
                .allowances(allowanceResponses)
                .build();
    }
}