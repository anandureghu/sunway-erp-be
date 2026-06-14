package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetStatus;
import com.erp.domain.finance.BudgetType;
import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.*;
import com.erp.repo.finance.BudgetHeaderRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BudgetService {

    private static final int MAX_REVISIONS = 3;

    private final BudgetHeaderRepository headerRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final AuthContext auth;
    private final CompanyRepository companyRepository;
    private final TransactionService transactionService;

    public BudgetService(
            BudgetHeaderRepository headerRepo,
            ChartOfAccountsRepository accountRepo,
            AuthContext auth,
            CompanyRepository companyRepository,
            TransactionService transactionService
    ) {
        this.headerRepo = headerRepo;
        this.accountRepo = accountRepo;
        this.auth = auth;
        this.companyRepository = companyRepository;
        this.transactionService = transactionService;
    }

    public BudgetResponseDTO createBudget(BudgetCreateDTO dto) {
        Long companyId = auth.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        BudgetType budgetType = parseBudgetType(dto.getBudgetType());
        String projectId = resolveProjectId(budgetType, dto.getProjectId());

        if (findActiveInScope(companyId, dto.getFiscalYear(), budgetType, projectId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An active budget already exists for this fiscal year and type");
        }

        ChartOfAccounts budgetAccount = resolveBudgetAccount(dto.getBudgetAccountId(), companyId);

        User user = User.builder().id(auth.getCurrentUserId()).build();

        BudgetHeader header = BudgetHeader.builder()
                .budgetName(dto.getBudgetName())
                .fiscalYear(dto.getFiscalYear())
                .budgetType(budgetType)
                .budgetAccount(budgetAccount)
                .projectId(projectId)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .amount(dto.getAmount())
                .distributedAmount(BigDecimal.ZERO)
                .status(BudgetStatus.IMPLEMENTED)
                .lines(new ArrayList<>())
                .company(company)
                .createdByUser(user)
                .build();

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO getBudget(Long id) {
        BudgetHeader header = getBHEntity(id);
        return toDTO(header);
    }

    public List<BudgetResponseDTO> listBudgets() {
        Long companyId = auth.getCurrentCompanyId();
        return headerRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public BudgetResponseDTO updateBudget(Long id, BudgetUpdateDTO dto) {
        BudgetHeader h = getBHEntity(id);
        if (h.getStatus() != BudgetStatus.APPROVED || !Boolean.TRUE.equals(h.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only an active approved budget can be revised");
        }
        return reviseApprovedBudget(h, dto);
    }

    private BudgetResponseDTO reviseApprovedBudget(BudgetHeader budget, BudgetUpdateDTO dto) {
        if (dto.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required to revise an approved budget");
        }
        if (budget.getAmount() != null && budget.getAmount().compareTo(dto.getAmount()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revised amount must differ from the current budget");
        }

        BudgetHeader root = budget.getParentBudget() == null ? budget : budget.getParentBudget();
        long reviseCount = headerRepo.countByParentBudgetId(root.getId());
        if (reviseCount >= MAX_REVISIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceeded maximum revise limit (" + MAX_REVISIONS + ")");
        }

        User user = User.builder().id(auth.getCurrentUserId()).build();
        BigDecimal oldAmount = budget.getAmount();

        root.setReviseCount((root.getReviseCount() == null ? 0 : root.getReviseCount()) + 1);
        headerRepo.save(root);

        budget.setIsActive(false);
        budget.setStatus(BudgetStatus.REVISED);
        headerRepo.save(budget);

        long nextRev = reviseCount + 1;
        BudgetHeader newHeader = BudgetHeader.builder()
                .parentBudget(root)
                .status(BudgetStatus.APPROVED)
                .budgetName(budget.getBudgetName() + " (Rev " + nextRev + ")")
                .fiscalYear(budget.getFiscalYear())
                .budgetType(budget.getBudgetType())
                .budgetAccount(budget.getBudgetAccount())
                .projectId(budget.getProjectId())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .amount(dto.getAmount())
                .distributedAmount(budget.getDistributedAmount() != null ? budget.getDistributedAmount() : BigDecimal.ZERO)
                .reviseCount(nextRev)
                .isActive(true)
                .company(budget.getCompany())
                .createdByUser(user)
                .approvedByUser(budget.getApprovedByUser())
                .build();

        BudgetHeader saved = headerRepo.save(newHeader);
        deactivateOthersInScope(saved);

        transactionService.recordBudgetRevisionAdjustment(saved, oldAmount, dto.getAmount());

        return toDTO(headerRepo.findById(saved.getId()).orElseThrow());
    }

    public BudgetResponseDTO activate(Long id) {
        BudgetHeader header = getBHEntity(id);

        User user = User.builder().id(auth.getCurrentUserId()).build();

        deactivateOthersInScope(header);

        header.setStatus(BudgetStatus.APPROVED);
        header.setIsActive(true);
        header.setApprovedByUser(user);

        BudgetHeader saved = headerRepo.save(header);
        transactionService.recordBudgetApproved(saved);

        return toDTO(saved);
    }

    public BudgetResponseDTO close(Long id) {
        BudgetHeader header = getBHEntity(id);
        header.setStatus(BudgetStatus.REJECTED);
        header.setIsActive(false);
        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO hold(Long id) {
        BudgetHeader header = getBHEntity(id);
        header.setStatus(BudgetStatus.HOLD);
        return toDTO(headerRepo.save(header));
    }

    @Transactional
    public BudgetResponseDTO distributeBudget(Long id, BudgetDistributeDTO dto) {
        BudgetHeader header = getBHEntity(id);

        if (header.getStatus() != BudgetStatus.APPROVED || !Boolean.TRUE.equals(header.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only an active approved budget can be distributed");
        }
        if (dto.getCreditAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit account is required");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        BigDecimal distributed = header.getDistributedAmount() != null ? header.getDistributedAmount() : BigDecimal.ZERO;
        BigDecimal total = header.getAmount() != null ? header.getAmount() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(distributed);
        if (dto.getAmount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Distribution exceeds remaining budget. Remaining: " + remaining);
        }

        ChartOfAccounts creditAccount = accountRepo.findById(dto.getCreditAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit account not found"));

        validateCreditAccountForBudgetType(header, creditAccount);

        LocalDate postedDate = dto.getPostedDate() != null ? dto.getPostedDate() : LocalDate.now();

        transactionService.recordBudgetDistribution(
                header, creditAccount, dto.getAmount(), dto.getNotes(), postedDate);

        header.setDistributedAmount(distributed.add(dto.getAmount()));
        return toDTO(headerRepo.save(header));
    }

    public List<BudgetDistributionResponseDTO> listDistributions(
            Long id, LocalDate from, LocalDate to, Boolean archived) {
        getBHEntity(id);
        return transactionService.listBudgetDistributions(id, from, to, archived);
    }

    public int archiveDistributions(Long id, LocalDate from, LocalDate to) {
        getBHEntity(id);
        return transactionService.archiveBudgetDistributions(id, from, to);
    }

    private void validateCreditAccountForBudgetType(BudgetHeader header, ChartOfAccounts account) {
        if (!account.getCompany().getId().equals(header.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to your company");
        }

        COAType type = account.getType();
        switch (header.getBudgetType()) {
            case OPEX -> {
                if (type != COAType.EXPENSE && type != COAType.COST) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "OPEX budgets can only be distributed to expense or cost accounts");
                }
            }
            case CAPEX -> {
                if (type != COAType.ASSET) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "CAPEX budgets can only be distributed to fixed asset accounts");
                }
            }
            case PROJECT -> {
                String budgetProject = header.getProjectId();
                String accountProject = account.getProjectCode();
                if (budgetProject == null || budgetProject.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project budget has no project ID");
                }
                if (accountProject == null || !budgetProject.equalsIgnoreCase(accountProject.trim())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Project budgets can only be distributed to accounts for the same project");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown budget type");
        }
    }

    private BudgetResponseDTO toDTO(BudgetHeader h) {
        BigDecimal amount = h.getAmount() != null ? h.getAmount() : BigDecimal.ZERO;
        BigDecimal distributed = h.getDistributedAmount() != null ? h.getDistributedAmount() : BigDecimal.ZERO;

        return BudgetResponseDTO.builder()
                .id(h.getId())
                .budgetName(h.getBudgetName())
                .fiscalYear(h.getFiscalYear())
                .budgetType(h.getBudgetType())
                .projectId(h.getProjectId())
                .budgetAccountId(h.getBudgetAccount() != null ? h.getBudgetAccount().getId() : null)
                .budgetAccountName(h.getBudgetAccount() != null ? h.getBudgetAccount().getAccountName() : null)
                .budgetAccountCode(h.getBudgetAccount() != null ? h.getBudgetAccount().getAccountCode() : null)
                .status(h.getStatus())
                .amount(h.getAmount())
                .distributedAmount(distributed)
                .remainingAmount(amount.subtract(distributed))
                .isActive(Boolean.TRUE.equals(h.getIsActive()))
                .reviseCount(h.getReviseCount())
                .parentBudgetId(h.getParentBudget() != null ? h.getParentBudget().getId() : null)
                .startDate(h.getStartDate())
                .endDate(h.getEndDate())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .companyId(h.getCompany().getId())
                .createdByUserId(h.getCreatedByUser() != null ? h.getCreatedByUser().getId() : null)
                .approvedByUserId(h.getApprovedByUser() != null ? h.getApprovedByUser().getId() : null)
                .lines(new ArrayList<>())
                .build();
    }

    private BudgetHeader getBHEntity(Long id) {
        Long companyId = auth.getCurrentCompanyId();
        return headerRepo.findById(id)
                .filter(bh -> bh.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Budget Header not found or access denied"));
    }

    private BudgetType parseBudgetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BudgetType.OPEX;
        }
        try {
            return BudgetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid budget type: " + raw);
        }
    }

    private String resolveProjectId(BudgetType type, String projectId) {
        if (type != BudgetType.PROJECT) {
            return null;
        }
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ID is required for project budgets");
        }
        return projectId.trim();
    }

    private ChartOfAccounts resolveBudgetAccount(Long budgetAccountId, Long companyId) {
        if (budgetAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget account is required");
        }
        ChartOfAccounts account = accountRepo.findById(budgetAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget account not found"));
        if (!account.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Budget account does not belong to your company");
        }
        if (account.getType() != COAType.BUDGET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected account must be a budget account");
        }
        return account;
    }

    private Optional<BudgetHeader> findActiveInScope(
            Long companyId, String fiscalYear, BudgetType budgetType, String projectId) {
        if (budgetType == BudgetType.PROJECT) {
            return headerRepo.findByCompanyIdAndFiscalYearAndBudgetTypeAndProjectIdAndIsActiveTrue(
                    companyId, fiscalYear, budgetType, projectId);
        }
        return headerRepo.findByCompanyIdAndFiscalYearAndBudgetTypeAndIsActiveTrueAndProjectIdIsNull(
                companyId, fiscalYear, budgetType);
    }

    private void deactivateOthersInScope(BudgetHeader header) {
        String projectId = header.getBudgetType() == BudgetType.PROJECT ? header.getProjectId() : null;
        headerRepo.deactivateOtherActivesForScope(
                header.getCompany().getId(),
                header.getFiscalYear(),
                header.getBudgetType(),
                projectId,
                header.getId());
    }
}
