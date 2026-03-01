package com.erp.service.common;

import com.erp.domain.Employee;
import com.erp.domain.hr.*;
import com.erp.dto.hr.AllowanceRequestDTO;
import com.erp.dto.hr.AllowanceResponseDTO;
import com.erp.dto.hr.ContractRequestDTO;
import com.erp.dto.hr.ContractResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ContractResponseDTO createContract(
            Long employeeId,
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

        mapAllowances(contract, dto.getAllowances());

        Contract saved = contractRepository.save(contract);

        return mapToResponse(saved);
    }

    // ================= UPDATE =================

    public ContractResponseDTO updateContract(
            Long contractId,
            ContractRequestDTO dto) {

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        contract.setContractType(dto.getContractType());
        contract.setStatus(dto.getStatus());
        contract.setEffectiveDate(dto.getEffectiveDate());
        contract.setExpirationDate(dto.getExpirationDate());
        contract.setNoticePeriodDays(dto.getNoticePeriodDays());

        // Clear old allowances
        contract.getAllowances().clear();

        mapAllowances(contract, dto.getAllowances());

        return mapToResponse(contract);
    }

    // ================= GET =================

    public ContractResponseDTO getByEmployee(Long employeeId) {

        Contract contract = contractRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        return mapToResponse(contract);
    }

    // ================= DELETE =================

    public void delete(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        contract.setDeleted(true);
    }

    // ================= MAPPING METHODS =================

    private void mapAllowances(
            Contract contract,
            List<AllowanceRequestDTO> allowanceDTOs) {

        List<SalaryAllowance> allowances = allowanceDTOs
                .stream()
                .map(a -> {

                    AllowanceType type = allowanceTypeRepository
                            .findById(a.getAllowanceTypeId())
                            .orElseThrow(() ->
                                    new RuntimeException("Allowance type not found"));

                    return SalaryAllowance.builder()
                            .allowanceType(type)
                            .amount(a.getAmount())
                            .effectiveDate(a.getEffectiveDate())
                            .note(a.getNote())
                            .contract(contract)
                            .build();
                })
                .toList();

        contract.getAllowances().addAll(allowances);
    }

    private ContractResponseDTO mapToResponse(Contract contract) {

        List<AllowanceResponseDTO> allowanceResponses =
                contract.getAllowances()
                        .stream()
                        .map(a -> AllowanceResponseDTO.builder()
                                .id(a.getId())
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