package com.erp.service.hr;

import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyProcessAccountDefault;
import com.erp.dto.hr.ProcessAccountDefaultDTO;
import com.erp.dto.hr.ProcessAccountDefaultsUpdateDTO;
import com.erp.dto.hr.ProcessAccountPair;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyProcessAccountDefaultRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ProcessAccountDefaultsService {

    private static final EnumSet<AccountingProcessCode> GL_BACKED_PROCESSES = EnumSet.of(
            AccountingProcessCode.MANUAL_JOURNAL,
            AccountingProcessCode.STOCK_VARIANCE);

    private final CompanyProcessAccountDefaultRepository repository;
    private final CompanyRepository companyRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuthContext authContext;

    public ProcessAccountDefaultsService(
            CompanyProcessAccountDefaultRepository repository,
            CompanyRepository companyRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            AuthContext authContext) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.authContext = authContext;
    }

    public List<ProcessAccountDefaultDTO> getProcessAccountDefaults(Long companyId) {
        assertCompanyAccess(companyId);
        return repository.findByCompanyIdOrderByProcessCodeAsc(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<ProcessAccountDefaultDTO> updateProcessAccountDefaults(
            Long companyId, ProcessAccountDefaultsUpdateDTO dto) {
        assertCompanyAccess(companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        List<ProcessAccountDefaultDTO> rows = dto.getDefaults() != null ? dto.getDefaults() : List.of();
        validateRows(rows);

        repository.deleteByCompanyId(companyId);

        Instant now = Instant.now();
        for (ProcessAccountDefaultDTO row : rows) {
            if (row.getProcessCode() == null) {
                continue;
            }
            Long debitId = resolveCoaId(companyId, row.getDebitAccountId(), "Debit account");
            Long creditId = resolveCoaId(companyId, row.getCreditAccountId(), "Credit account");
            validateAccountPair(row.getProcessCode(), debitId, creditId);

            CompanyProcessAccountDefault entity = CompanyProcessAccountDefault.builder()
                    .company(company)
                    .processCode(row.getProcessCode())
                    .debitAccountId(debitId)
                    .creditAccountId(creditId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repository.save(entity);
        }

        return getProcessAccountDefaults(companyId);
    }

    public Optional<ProcessAccountPair> resolveProcessDefaults(
            Long companyId, AccountingProcessCode processCode) {
        return repository.findByCompanyIdAndProcessCode(companyId, processCode)
                .filter(row -> row.getDebitAccountId() != null && row.getCreditAccountId() != null)
                .map(row -> new ProcessAccountPair(row.getDebitAccountId(), row.getCreditAccountId()));
    }

    private void assertCompanyAccess(Long companyId) {
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId == null || !currentCompanyId.equals(companyId)) {
            throw new RuntimeException("Not allowed to access this company's process account defaults");
        }
    }

    private void validateRows(List<ProcessAccountDefaultDTO> rows) {
        Set<AccountingProcessCode> seen = new HashSet<>();
        for (ProcessAccountDefaultDTO row : rows) {
            if (row.getProcessCode() == null) {
                throw new RuntimeException("Process is required for each default account row");
            }
            if (!seen.add(row.getProcessCode())) {
                throw new RuntimeException("Duplicate process: " + row.getProcessCode());
            }
        }
    }

    private void validateAccountPair(
            AccountingProcessCode processCode, Long debitId, Long creditId) {
        boolean hasDebit = debitId != null;
        boolean hasCredit = creditId != null;
        if (hasDebit != hasCredit) {
            throw new RuntimeException(
                    "Both debit and credit accounts are required for " + processCode);
        }
        if (GL_BACKED_PROCESSES.contains(processCode) && (!hasDebit || !hasCredit)) {
            throw new RuntimeException(
                    "Debit and credit accounts are required for " + processCode);
        }
    }

    private Long resolveCoaId(Long companyId, Long coaId, String label) {
        if (coaId == null) {
            return null;
        }
        ChartOfAccounts coa = chartOfAccountsRepository.findById(coaId)
                .orElseThrow(() -> new RuntimeException(label + " not found"));
        if (!coa.getCompany().getId().equals(companyId)) {
            throw new RuntimeException(label + " does not belong to this company");
        }
        return coaId;
    }

    private ProcessAccountDefaultDTO toDto(CompanyProcessAccountDefault entity) {
        return ProcessAccountDefaultDTO.builder()
                .id(entity.getId())
                .processCode(entity.getProcessCode())
                .debitAccountId(entity.getDebitAccountId())
                .creditAccountId(entity.getCreditAccountId())
                .build();
    }
}
