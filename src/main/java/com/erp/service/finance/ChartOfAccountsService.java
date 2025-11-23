package com.erp.service.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreateAccountDTO;
import com.erp.dto.finance.UpdateAccountDTO;
import com.erp.dto.finance.ChartOfAccountResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository repo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;

    public ChartOfAccountsService(
            ChartOfAccountsRepository repo,
            CompanyRepository companyRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.auth = auth;
    }

    // =============================================================
    // CREATE ACCOUNT
    // =============================================================
    public ChartOfAccountResponseDTO createAccount(CreateAccountDTO dto) {

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // permission check
//        if (!company.getCreatedBy().equals(String.valueOf(auth.getCurrentUserId()))) {
//            throw new RuntimeException("Not allowed");
//        }

        ChartOfAccounts parent = null;
        if (dto.getParentId() != null) {
            parent = repo.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent account not found"));
        }

        ChartOfAccounts acc = ChartOfAccounts.builder()
                .company(company)
                .accountCode(dto.getAccountCode())
                .accountName(dto.getAccountName())
                .description(dto.getDescription())
                .currency(dto.getCurrency())
                .type(dto.getType())
                .status(dto.getStatus())
                .parent(parent)
                .glAccountClassTypeKey(dto.getGlAccountClassTypeKey())
                .glAccountType(dto.getGlAccountType())
                .balance(dto.getOpeningBalance() == null ? null : dto.getOpeningBalance())
                .build();

        return toDTO(repo.save(acc));
    }

    // =============================================================
    // UPDATE ACCOUNT
    // =============================================================
    public ChartOfAccountResponseDTO updateAccount(Long id, UpdateAccountDTO dto) {

        ChartOfAccounts acc = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        acc.setAccountName(dto.getAccountName());
        acc.setDescription(dto.getDescription());
        acc.setStatus(dto.getStatus());
        acc.setGlAccountClassTypeKey(dto.getGlAccountClassTypeKey());
        acc.setGlAccountType(dto.getGlAccountType());

        return toDTO(repo.save(acc));
    }


    // =============================================================
    // LIST ALL FOR COMPANY
    // =============================================================
    public List<ChartOfAccountResponseDTO> listAll(Long companyId) {
        return repo.findByCompanyId(companyId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =============================================================
    // GET
    // =============================================================
    public ChartOfAccountResponseDTO getById(Long id) {
        return repo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    // =============================================================
    // DELETE ACCOUNT
    // =============================================================
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // =============================================================
    // HELPER: Find by Code
    // =============================================================
    public ChartOfAccounts getByCodeOrThrow(String code) {
        return repo.findByAccountCode(code)
                .orElseThrow(() -> new RuntimeException("Account not found: " + code));
    }

    // =============================================================
    // REQUIRED BY TRANSACTION & PAYMENT SERVICES
    // =============================================================

    // Get company bank account (Asset)
    public String getCompanyBankAccountCode(Long companyId) {
        Optional<ChartOfAccounts> acc =
                repo.findTopByCompanyIdAndType(companyId, "asset");

        return acc.map(ChartOfAccounts::getAccountCode)
                .orElse("BANK-DEFAULT");
    }

    // Get Accounts Receivable (AR) account
    public String getCustomerARAccountCode(Long companyId) {
        Optional<ChartOfAccounts> acc =
                repo.findTopByCompanyIdAndType(companyId, "asset")
                        .filter(a -> a.getGlAccountType().equalsIgnoreCase("AR"));

        return acc.map(ChartOfAccounts::getAccountCode)
                .orElse("AR-DEFAULT");
    }

    // Get Accounts Payable (AP)
    public String getVendorAPAccountCode(Long companyId) {
        return repo.findTopByCompanyIdAndType(companyId, "liability")
                .map(ChartOfAccounts::getAccountCode)
                .orElse("AP-DEFAULT");
    }

    // Revenue account for invoices
    public String getRevenueAccountCode(Long companyId) {
        return repo.findTopByCompanyIdAndType(companyId, "income")
                .map(ChartOfAccounts::getAccountCode)
                .orElse("REV-DEFAULT");
    }

    // Expenses
    public String getExpenseAccountCode(Long companyId) {
        return repo.findTopByCompanyIdAndType(companyId, "expense")
                .map(ChartOfAccounts::getAccountCode)
                .orElse("EXP-DEFAULT");
    }

    // =============================================================
    // DEFAULT COA BOOTSTRAP (call when company is created)
    // =============================================================
    public void createDefaultCOAForCompany(Company c) {

        repo.save(ChartOfAccounts.builder()
                .company(c)
                .accountCode("BANK-001")
                .accountName("Cash at Bank")
                .type("asset")
                .status("active")
                .build());

        repo.save(ChartOfAccounts.builder()
                .company(c)
                .accountCode("AR-001")
                .accountName("Accounts Receivable")
                .type("asset")
                .glAccountType("AR")
                .status("active")
                .build());

        repo.save(ChartOfAccounts.builder()
                .company(c)
                .accountCode("AP-001")
                .accountName("Accounts Payable")
                .type("liability")
                .glAccountType("AP")
                .status("active")
                .build());

        repo.save(ChartOfAccounts.builder()
                .company(c)
                .accountCode("REV-001")
                .accountName("Sales Revenue")
                .type("income")
                .status("active")
                .build());

        repo.save(ChartOfAccounts.builder()
                .company(c)
                .accountCode("EXP-001")
                .accountName("Expense Account")
                .type("expense")
                .status("active")
                .build());
    }

    // =============================================================
    // DTO MAPPER
    // =============================================================
    private ChartOfAccountResponseDTO toDTO(ChartOfAccounts acc) {
        return ChartOfAccountResponseDTO.builder()
                .id(acc.getId())
                .accountCode(acc.getAccountCode())
                .accountName(acc.getAccountName())
                .description(acc.getDescription())
                .type(acc.getType())
                .parentId(acc.getParent() != null ? acc.getParent().getId() : null)
                .currency(acc.getCurrency())
                .status(acc.getStatus())
                .companyId(acc.getCompany().getId())
                .glAccountType(acc.getGlAccountType())
                .glAccountClassTypeKey(acc.getGlAccountClassTypeKey())
                .balance(acc.getBalance())
                .build();
    }
}
