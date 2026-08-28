package com.erp.service.common;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.enums.ContractStatus;
import com.erp.domain.hr.AllowanceType;
import com.erp.domain.hr.Contract;
import com.erp.domain.hr.SalaryAllowance;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.hr.AllowanceRequestDTO;
import com.erp.dto.hr.AllowanceResponseDTO;
import com.erp.dto.hr.ContractRequestDTO;
import com.erp.dto.hr.ContractResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.AllowanceTypeRepository;
import com.erp.repo.hr.ContractRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
    private final FileStorageService fileStorageService;
    private final AuthContext authContext;

    // ================= CREATE =================

    public ContractResponseDTO createContract(Long employeeId, ContractRequestDTO dto) {
        return createContract(employeeId, dto, null);
    }

    public ContractResponseDTO createContract(Long employeeId, ContractRequestDTO dto, MultipartFile attachment) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));

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
                .company(employee.getCompany())
                .allowances(new ArrayList<>())
                .build();

        // Status is action-driven, never taken from the request.
        contract.setStatus(deriveStatus(contract));

        mapAllowances(contract, dto.getAllowances());

        Contract saved = contractRepository.save(contract);
        uploadAttachmentIfPresent(saved, attachment);
        return mapToResponse(saved);
    }

    // ================= UPDATE =================

    public ContractResponseDTO updateContract(Long contractId, ContractRequestDTO dto) {
        return updateContract(contractId, dto, null);
    }

    public ContractResponseDTO updateContract(Long contractId, ContractRequestDTO dto, MultipartFile attachment) {

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
        assertSameTenant(contract);

        if (contract.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot update deleted contract");
        }

        contract.setContractType(dto.getContractType());
        contract.setEffectiveDate(dto.getEffectiveDate());
        contract.setExpirationDate(dto.getExpirationDate());
        contract.setContractPeriodMonths(dto.getContractPeriodMonths());
        contract.setNoticePeriodDays(dto.getNoticePeriodDays());
        contract.setSalaryRateType(dto.getSalaryRateType());
        contract.setSignatureDate(dto.getSignatureDate());
        contract.setSignedBy(dto.getSignedBy());
        if (dto.getAttachmentUrl() != null && !dto.getAttachmentUrl().isBlank()) {
            contract.setAttachmentPath(dto.getAttachmentUrl());
        }
        contract.setTermsAndConditions(dto.getTermsAndConditions());

        contract.getAllowances().clear();
        mapAllowances(contract, dto.getAllowances());

        // Status is action-driven, never taken from the request.
        contract.setStatus(deriveStatus(contract));

        Contract saved = contractRepository.save(contract);
        uploadAttachmentIfPresent(saved, attachment);
        return mapToResponse(saved);
    }

    public ContractResponseDTO updateContractAttachment(Long contractId, MultipartFile attachment) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
        assertSameTenant(contract);

        if (contract.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot update deleted contract");
        }

        uploadAttachmentIfPresent(contract, attachment);
        return mapToResponse(contract);
    }

    public ContractResponseDTO updateEmployeeContractAttachment(Long employeeId, MultipartFile attachment) {
        Contract contract = contractRepository
                .findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Create the contract before uploading an attachment"));
        assertSameTenant(contract);

        uploadAttachmentIfPresent(contract, attachment);
        return mapToResponse(contract);
    }

    // ================= GET =================

    @Transactional(readOnly = true)
    public ContractResponseDTO getByEmployee(Long employeeId) {
        return contractRepository
                .findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(employeeId)
                .map(contract -> {
                    assertSameTenant(contract);
                    return mapToResponse(contract);
                })
                .orElse(null);
    }

    // ================= RENEWALS =================

    /** Contracts HR should review for renewal — active or already expired, soonest first. */
    @Transactional(readOnly = true)
    public List<ContractResponseDTO> listRenewables() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) return List.of();
        return contractRepository
                .findByCompany_IdAndDeletedFalseAndStatusInOrderByExpirationDateAsc(
                        companyId, List.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED))
                .stream()
                .filter(c -> c.getExpirationDate() != null)
                .map(this::mapToResponse)
                .toList();
    }

    /** Renew a contract — extend its expiry (explicit date, or by its period) and set it ACTIVE. */
    public ContractResponseDTO renewContract(Long contractId, LocalDate newExpirationDate) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
        assertSameTenant(contract);

        // A lapsed contract extends forward from today, not from a past expiry.
        LocalDate base = contract.getExpirationDate() != null
                ? contract.getExpirationDate() : LocalDate.now();
        if (base.isBefore(LocalDate.now())) {
            base = LocalDate.now();
        }
        int months = contract.getContractPeriodMonths() != null
                && contract.getContractPeriodMonths() > 0
                ? contract.getContractPeriodMonths() : 12;
        LocalDate resolved = newExpirationDate != null
                ? newExpirationDate : base.plusMonths(months);

        if (!resolved.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A renewed contract must expire in the future.");
        }
        contract.setExpirationDate(resolved);
        contract.setStatus(ContractStatus.ACTIVE);
        return mapToResponse(contract);
    }

    /** Termination reason recorded on an employee whose contract runs out. */
    public static final String NON_RENEWAL_CODE = "Non-renewal of contract";

    /** Let a contract expire — mark it EXPIRED without renewing, and stand the employee down. */
    public ContractResponseDTO expireContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
        assertSameTenant(contract);
        endContract(contract);
        return mapToResponse(contract);
    }

    /**
     * Mark a contract EXPIRED and stand its employee down: status INACTIVE with the
     * "Non-renewal of contract" termination code. Shared by the manual "let expire"
     * action and the scheduled contract-end sweep.
     */
    private void endContract(Contract contract) {
        contract.setStatus(ContractStatus.EXPIRED);
        Employee employee = contract.getEmployee();
        if (employee != null
                && (employee.getStatus() == null || !employee.getStatus().isDepartedOrInactive())) {
            employee.setStatus(EmployeeStatus.INACTIVE);
            employee.setTerminationCode(NON_RENEWAL_CODE);
            employeeRepository.save(employee);
        }
    }

    /**
     * Scheduled sweep across all tenants: any still-ACTIVE contract whose expiration
     * date has passed is ended (EXPIRED + employee set inactive). Returns the count.
     */
    public int sweepLapsedContracts(LocalDate asOf) {
        List<Contract> lapsed = contractRepository
                .findByDeletedFalseAndStatusAndExpirationDateBefore(ContractStatus.ACTIVE, asOf);
        lapsed.forEach(this::endContract);
        return lapsed.size();
    }

    // ================= DELETE =================

    public void delete(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contract not found"));
        assertSameTenant(contract);

        contract.setDeleted(true);
    }

    // ================= TENANT GUARD =================

    private void assertSameTenant(Contract contract) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long contractCompanyId = contract != null && contract.getCompany() != null
                ? contract.getCompany().getId() : null;
        if (currentCompanyId == null || contractCompanyId == null
                || !currentCompanyId.equals(contractCompanyId)) {
            throw new AccessDeniedException("This contract belongs to a different company");
        }
    }

    // ================= ALLOWANCE MAPPING =================

    private void mapAllowances(Contract contract, List<AllowanceRequestDTO> allowanceDTOs) {

        if (allowanceDTOs == null || allowanceDTOs.isEmpty()) {
            return;
        }

        for (AllowanceRequestDTO dto : allowanceDTOs) {

            boolean hasTypeId = dto.getAllowanceTypeId() != null;
            boolean hasCustomName = dto.getCustomName() != null && !dto.getCustomName().trim().isBlank();

            if (!hasTypeId && !hasCustomName) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Allowance type is required"
                );
            }

            AllowanceType type = null;
            String customName = null;

            if (hasTypeId) {
                type = allowanceTypeRepository.findById(dto.getAllowanceTypeId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Allowance type not found: " + dto.getAllowanceTypeId()
                        ));
            } else {
                customName = dto.getCustomName().trim();
            }

            SalaryAllowance allowance = SalaryAllowance.builder()
                    .allowanceType(type)
                    .customName(customName)
                    .amount(dto.getAmount())
                    .effectiveDate(dto.getEffectiveDate())
                    .note(dto.getNote())
                    .contract(contract)
                    .build();

            contract.getAllowances().add(allowance);
        }
    }

    private void uploadAttachmentIfPresent(Contract contract, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return;
        }

        FileUploadResult upload = fileStorageService.upload(
                attachment,
                FileCategory.CONTRACT_ATTACHMENT,
                contract.getId().toString(),
                false,
                authContext.getCurrentCompanyId()
        );
        contract.setAttachmentPath(upload.getBlobPath());
        // Uploading a signed copy activates the contract (unless it already ended).
        contract.setStatus(deriveStatus(contract));
        contractRepository.save(contract);
    }

    /**
     * Contract status is derived from actions, never set by hand:
     *  <ul>
     *    <li>TERMINATED / EXPIRED are terminal — set by the employee-exit and
     *        contract-end actions — and are always kept.</li>
     *    <li>A contract that has been signed electronically (signature recorded) or
     *        has a signed copy uploaded (attachment present) is ACTIVE.</li>
     *    <li>Anything else is still a DRAFT.</li>
     *  </ul>
     */
    private ContractStatus deriveStatus(Contract c) {
        if (c.getStatus() == ContractStatus.TERMINATED || c.getStatus() == ContractStatus.EXPIRED) {
            return c.getStatus();
        }
        boolean signed = c.getSignatureDate() != null
                || (c.getSignedBy() != null && !c.getSignedBy().isBlank());
        boolean hasSignedCopy = c.getAttachmentPath() != null && !c.getAttachmentPath().isBlank();
        return (signed || hasSignedCopy) ? ContractStatus.ACTIVE : ContractStatus.DRAFT;
    }

    // ================= RESPONSE MAPPING =================

    private ContractResponseDTO mapToResponse(Contract contract) {

        List<AllowanceResponseDTO> allowanceResponses =
                contract.getAllowances()
                        .stream()
                        .map(a -> AllowanceResponseDTO.builder()
                                .id(a.getId())
                                .allowanceTypeId(a.getAllowanceType() != null ? a.getAllowanceType().getId() : null)
                                .allowanceType(a.getAllowanceType() != null
                                        ? a.getAllowanceType().getName()
                                        : a.getCustomName())
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
                .attachmentUrl(resolveAttachmentUrl(contract.getAttachmentPath()))
                .employeeId(contract.getEmployee().getId())
                .staffName(contract.getEmployee().getFirstName() + " " + contract.getEmployee().getLastName())
                .allowances(allowanceResponses)
                .build();
    }

    private String resolveAttachmentUrl(String attachmentPath) {
        if (attachmentPath == null || attachmentPath.isBlank()) {
            return null;
        }
        if (attachmentPath.startsWith("http://") || attachmentPath.startsWith("https://")) {
            return attachmentPath;
        }
        return fileStorageService.getPrivateSasUrl(attachmentPath);
    }
}
