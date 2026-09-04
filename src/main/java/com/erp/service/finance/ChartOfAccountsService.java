package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.GLAccountBalance;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.ChartOfAccountResponseDTO;
import com.erp.dto.finance.CreateAccountDTO;
import com.erp.dto.finance.UpdateAccountDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.GLAccountBalanceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository repo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthContext auth;
    private final TransactionService transactionService;
    private final GLAccountBalanceRepository glAccountBalanceRepository;

    public ChartOfAccountsService(
            ChartOfAccountsRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            AuthContext auth,
            TransactionService transactionService,
            GLAccountBalanceRepository glAccountBalanceRepository
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.auth = auth;
        this.transactionService = transactionService;
        this.glAccountBalanceRepository = glAccountBalanceRepository;
    }

    // =============================================================
    // CREATE ACCOUNT
    // =============================================================
    @Transactional
    public ChartOfAccountResponseDTO createAccount(CreateAccountDTO dto) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChartOfAccounts parent = null;
        if (dto.getParentId() != null) {
            parent = repo.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent account not found"));
            if (!parent.getCompany().getId().equals(companyId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent account does not belong to your company");
            }
        }

        COAType type = COAType.valueOf(dto.getType());
        String accountNo = dto.getAccountNo() != null ? dto.getAccountNo().trim() : "";
        if (accountNo.isEmpty()) {
            accountNo = nextAccountNo(companyId, type);
        } else if (repo.existsByCompany_IdAndAccountNo(companyId, accountNo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account number " + accountNo + " already exists for this company. Use the next available number.");
        }

        ChartOfAccounts acc = ChartOfAccounts.builder()
                .company(company)
                .accountCode(dto.getAccountCode())
                .accountName(dto.getAccountName())
                .description(dto.getDescription())
                .type(type)
                .isActive(true)
                .parent(parent)
                .balance(null)
                .initialBalanceSet(false)
                .interCompanyNumber(dto.getInterCompanyNumber())
                .accountNo(accountNo)
                .asOfDate(Instant.now())
                .projectCode(dto.getProjectCode())
                .department(department)
                .createdBy(user)
                .build();

        ChartOfAccounts saved = repo.save(acc);

        if (dto.getOpeningBalance() != null) {
            if (dto.getOpeningBalance().compareTo(BigDecimal.ZERO) != 0) {
                transactionService.recordOpeningBalanceCreditOnly(saved.getId(), dto.getOpeningBalance());
            }
            saved = repo.findById(saved.getId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));
            saved.setInitialBalanceSet(true);
            saved.setAsOfDate(Instant.now());
            saved = repo.save(saved);
        }

        return toDTO(saved);
    }

    // =============================================================
    // UPDATE ACCOUNT
    // =============================================================
    public ChartOfAccountResponseDTO updateAccount(Long id, UpdateAccountDTO dto) {

        User user = userRepository.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChartOfAccounts acc = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        assertCoaInTenant(acc);

        acc.setAccountName(dto.getAccountName());
        acc.setDescription(dto.getDescription());

        acc.setUpdatedBy(user);

        return toDTO(repo.save(acc));
    }

    @Transactional
    public ChartOfAccountResponseDTO setInitialBalance(Long id, BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }
        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChartOfAccounts acc = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!acc.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to your company");
        }
        if (Boolean.TRUE.equals(acc.getInitialBalanceSet())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Initial balance has already been set for this account");
        }

        transactionService.recordOpeningBalanceCreditOnly(id, amount);

        ChartOfAccounts updated = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        updated.setAsOfDate(Instant.now());
        updated.setInitialBalanceSet(true);
        updated.setUpdatedBy(user);

        return toDTO(repo.save(updated));
    }


    // =============================================================
    // LIST ALL FOR COMPANY
    // =============================================================
    public List<ChartOfAccountResponseDTO> listAll() {
        Long companyId = auth.getCurrentCompanyId();
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Next unique account number for the company and account type.
     * Numeric types use a 100000-based series (ASSET 1xxxxx, LIABILITY 2xxxxx, …);
     * BUDGET uses BUD{year} then BUD{year}-2, etc. if taken.
     */
    public String nextAccountNo(String typeRaw) {
        Long companyId = auth.getCurrentCompanyId();
        COAType type;
        try {
            type = COAType.valueOf(typeRaw);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account type");
        }
        return nextAccountNo(companyId, type);
    }

    private String nextAccountNo(Long companyId, COAType type) {
        if (type == COAType.BUDGET) {
            String base = "BUD" + java.time.Year.now().getValue();
            if (!repo.existsByCompany_IdAndAccountNo(companyId, base)) {
                return base;
            }
            for (int i = 2; i < 1000; i++) {
                String candidate = base + "-" + i;
                if (!repo.existsByCompany_IdAndAccountNo(companyId, candidate)) {
                    return candidate;
                }
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No available budget account number");
        }

        int series = accountNoSeries(type);
        int maxInSeries = series - 1;
        for (String no : repo.findActiveAccountNosByCompanyId(companyId)) {
            if (no == null || !no.matches("\\d{6}")) continue;
            int n = Integer.parseInt(no);
            if (n / 100000 == series / 100000) {
                maxInSeries = Math.max(maxInSeries, n);
            }
        }
        int next = maxInSeries + 1;
        if (next / 100000 != series / 100000 || next > series + 99999) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No available account numbers left in the " + type + " series");
        }
        return String.format("%06d", next);
    }

    /** Matches frontend COA type → base account number mapping. */
    private static int accountNoSeries(COAType type) {
        return switch (type) {
            case ASSET -> 100000;
            case LIABILITY -> 200000;
            case EQUITY -> 300000;
            case REVENUE -> 400000;
            case COST -> 500000;
            case EXPENSE -> 600000;
            case TAX -> 700000;
            case CASH -> 800000;
            case INCOME -> 900000;
            case BUDGET -> 0;
        };
    }

    // =============================================================
    // GET
    // =============================================================
    public ChartOfAccountResponseDTO getById(Long id) {
        ChartOfAccounts acc = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        assertCoaInTenant(acc);
        return toDTO(acc);
    }

    /**
     * GL balance for fiscal year; account must belong to the current company (or SUPER_ADMIN).
     */
    public GLAccountBalance getGlBalanceForAccount(Long accountId, String fiscalYear) {
        ChartOfAccounts acc = repo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        assertCoaInTenant(acc);
        return glAccountBalanceRepository.findByAccountIdAndFiscalYear(accountId, fiscalYear).orElse(null);
    }

    // =============================================================
    // DELETE ACCOUNT
    // =============================================================
    public void delete(Long id) {
        ChartOfAccounts account = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        assertCoaInTenant(account);
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot delete account, balance is greater than 0");
        }
        account.setIsActive(false);
        repo.save(account);
    }


    private boolean isSuperAdmin() {
        String r = auth.getCurrentUserRole();
        return r != null && "SUPER_ADMIN".equalsIgnoreCase(r);
    }

    private void assertCoaInTenant(ChartOfAccounts acc) {
        if (isSuperAdmin()) {
            return;
        }
        Long cid = auth.getCurrentCompanyId();
        if (cid == null || acc.getCompany() == null || !cid.equals(acc.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account not found or access denied");
        }
    }

    // =============================================================
    // DTO MAPPER
    // =============================================================
    private ChartOfAccountResponseDTO toDTO(ChartOfAccounts acc) {
        return ChartOfAccountResponseDTO.builder()
                .id(acc.getId())
                .accountNo(acc.getAccountNo())
                .accountCode(acc.getAccountCode())
                .accountName(acc.getAccountName())
                .description(acc.getDescription())
                .type(String.valueOf(acc.getType()))
                .active(acc.getIsActive())
                .balance(acc.getBalance())
                .companyId(acc.getCompany().getId())
                .parentId(acc.getParent() != null ? acc.getParent().getId() : null)
                .parentName(acc.getParent() != null ? acc.getParent().getAccountName() : null)
                .parentAccountNo(acc.getParent() != null ? acc.getParent().getAccountNo() : null)
                .parentCode(acc.getParent() != null ? acc.getParent().getAccountCode() : null)
                .parentType(acc.getParent() != null ? String.valueOf(acc.getParent().getType()) : null)
                .departmentId(acc.getDepartment() != null ? acc.getDepartment().getId() : null)
                .departmentCode(acc.getDepartment() != null ? acc.getDepartment().getDepartmentCode() : null)
                .departmentName(acc.getDepartment() != null ? acc.getDepartment().getDepartmentName() : null)
                .projectCode(acc.getProjectCode())
                .initialBalanceSet(Boolean.TRUE.equals(acc.getInitialBalanceSet()))
                .createdById(acc.getCreatedBy().getId())
                .createdByName(acc.getCreatedBy().getFullName())
                .createdAt(acc.getCreatedAt())
                .updatedAt(acc.getUpdatedAt())
                .updatedById(acc.getUpdatedBy() != null ? acc.getUpdatedBy().getId() : null)
                .updatedByName(acc.getUpdatedBy() != null ? acc.getUpdatedBy().getFullName() : null)
                .interCompanyNumber(acc.getInterCompanyNumber())
                .asOfDate(acc.getAsOfDate())
                .build();
    }

    public void updateBalance(ChartOfAccounts acc, BigDecimal amount) {
        BigDecimal cur = acc.getBalance() == null ? BigDecimal.ZERO : acc.getBalance();
        CoaBalanceRules.assertSufficientBalance(acc, amount);
        acc.setBalance(cur.add(amount));
        repo.save(acc);
    }
}
