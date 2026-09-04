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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProcessAccountDefaultsService {

    private static final EnumSet<AccountingProcessCode> GL_BACKED_PROCESSES = EnumSet.of(
            AccountingProcessCode.MANUAL_JOURNAL,
            AccountingProcessCode.STOCK_VARIANCE,
            AccountingProcessCode.PAYROLL,
            AccountingProcessCode.OTHER_PAYMENT);

    // End-of-Service benefits post to a single GL account (the EOS expense/liability),
    // so only a debit account is selected — the payment side rides the payroll posting.
    private static final EnumSet<AccountingProcessCode> DEBIT_ONLY_PROCESSES = EnumSet.of(
            AccountingProcessCode.END_OF_SERVICE);

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
        List<ProcessAccountDefaultDTO> rows = new java.util.ArrayList<>(
                repository.findByCompanyIdOrderByProcessCodeAsc(companyId).stream()
                        .map(this::toDto)
                        .toList());

        // End-of-Service benefits default to the company's "End of Service" GL account
        // when nothing has been configured yet, so the selection is pre-filled.
        boolean hasEos = rows.stream()
                .anyMatch(r -> r.getProcessCode() == AccountingProcessCode.END_OF_SERVICE
                        && r.getDebitAccountId() != null);
        if (!hasEos) {
            Long eosAccountId = resolveEndOfServiceAccountId(companyId);
            if (eosAccountId != null) {
                rows.removeIf(r -> r.getProcessCode() == AccountingProcessCode.END_OF_SERVICE);
                rows.add(ProcessAccountDefaultDTO.builder()
                        .processCode(AccountingProcessCode.END_OF_SERVICE)
                        .debitAccountId(eosAccountId)
                        .build());
            }
        }
        return rows;
    }

    /**
     * The GL account End-of-Service benefits post to: the saved END_OF_SERVICE debit
     * account, else the company's account whose name contains "end of service".
     */
    public Long resolveEndOfServiceAccountId(Long companyId) {
        Long saved = resolveProcessDebitAccount(companyId, AccountingProcessCode.END_OF_SERVICE)
                .orElse(null);
        if (saved != null) {
            return saved;
        }
        return chartOfAccountsRepository
                .findFirstByCompanyIdAndAccountNameContainingIgnoreCaseOrderByIdAsc(
                        companyId, "end of service")
                .map(com.erp.domain.finance.ChartOfAccounts::getId)
                .orElse(null);
    }

    @Transactional
    public List<ProcessAccountDefaultDTO> updateProcessAccountDefaults(
            Long companyId, ProcessAccountDefaultsUpdateDTO dto) {
        assertCompanyAccess(companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        List<ProcessAccountDefaultDTO> rows = dto.getDefaults() != null ? dto.getDefaults() : List.of();
        validateRows(rows);

        Set<AccountingProcessCode> incoming = rows.stream()
                .map(ProcessAccountDefaultDTO::getProcessCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccountingProcessCode.class)));

        Instant now = Instant.now();
        for (ProcessAccountDefaultDTO row : rows) {
            if (row.getProcessCode() == null) {
                continue;
            }
            Long debitId = resolveCoaId(companyId, row.getDebitAccountId(), "Debit account");
            Long creditId = resolveCoaId(companyId, row.getCreditAccountId(), "Credit account");
            validateAccountPair(row.getProcessCode(), debitId, creditId);

            CompanyProcessAccountDefault entity = repository
                    .findByCompanyIdAndProcessCode(companyId, row.getProcessCode())
                    .orElseGet(() -> CompanyProcessAccountDefault.builder()
                            .company(company)
                            .processCode(row.getProcessCode())
                            .createdAt(now)
                            .build());
            entity.setDebitAccountId(debitId);
            entity.setCreditAccountId(creditId);
            entity.setUpdatedAt(now);
            repository.save(entity);
        }

        repository.findByCompanyIdOrderByProcessCodeAsc(companyId).stream()
                .filter(existing -> !incoming.contains(existing.getProcessCode()))
                .forEach(repository::delete);

        return getProcessAccountDefaults(companyId);
    }

    public Optional<ProcessAccountPair> resolveProcessDefaults(
            Long companyId, AccountingProcessCode processCode) {
        return repository.findByCompanyIdAndProcessCode(companyId, processCode)
                .filter(row -> row.getDebitAccountId() != null && row.getCreditAccountId() != null)
                .map(row -> new ProcessAccountPair(row.getDebitAccountId(), row.getCreditAccountId()));
    }

    public Optional<Long> resolveProcessDebitAccount(
            Long companyId, AccountingProcessCode processCode) {
        return repository.findByCompanyIdAndProcessCode(companyId, processCode)
                .map(CompanyProcessAccountDefault::getDebitAccountId)
                .filter(Objects::nonNull);
    }

    public Optional<Long> resolveProcessCreditAccount(
            Long companyId, AccountingProcessCode processCode) {
        return repository.findByCompanyIdAndProcessCode(companyId, processCode)
                .map(CompanyProcessAccountDefault::getCreditAccountId)
                .filter(Objects::nonNull);
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
        if (DEBIT_ONLY_PROCESSES.contains(processCode)) {
            if (creditId != null) {
                throw new RuntimeException(
                        "Credit account is not used for " + processCode);
            }
            return;
        }

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
