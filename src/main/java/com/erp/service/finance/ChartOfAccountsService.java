package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.ChartOfAccountResponseDTO;
import com.erp.dto.finance.CreateAccountDTO;
import com.erp.dto.finance.UpdateAccountDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
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

    public ChartOfAccountsService(
            ChartOfAccountsRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            AuthContext auth,
            TransactionService transactionService
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.auth = auth;
        this.transactionService = transactionService;
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
        }

//        TODO: check with ali, that multiple account can exist with the same type, or a single type parent account?
        ChartOfAccounts acc = ChartOfAccounts.builder()
                .company(company)
                .accountCode(dto.getAccountCode())
                .accountName(dto.getAccountName())
                .description(dto.getDescription())
                .type(COAType.valueOf(dto.getType()))
                .isActive(true)
                .parent(parent)
                .balance(null)
                .initialBalanceSet(false)
                .interCompanyNumber(dto.getInterCompanyNumber())
                .accountNo(dto.getAccountNo())
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
        ChartOfAccounts account = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot delete account, balance is greater than 0");
        }
        account.setIsActive(false);
        repo.save(account);
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
