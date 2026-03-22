package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.BudgetStatus;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.*;
import com.erp.repo.finance.BudgetHeaderRepository;
import com.erp.repo.finance.BudgetLineRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class BudgetService {

    private final BudgetHeaderRepository headerRepo;
    private final BudgetLineRepository lineRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final DepartmentRepository deptRepo;
    private final AuthContext auth;
    private final CompanyRepository companyRepository;
    private final TransactionService transactionService;

    public BudgetService(
            BudgetHeaderRepository headerRepo,
            BudgetLineRepository lineRepo,
            ChartOfAccountsRepository accountRepo,
            DepartmentRepository deptRepo,
            AuthContext auth,
            CompanyRepository companyRepository,
            TransactionService transactionService
    ) {
        this.headerRepo = headerRepo;
        this.lineRepo = lineRepo;
        this.accountRepo = accountRepo;
        this.deptRepo = deptRepo;
        this.auth = auth;
        this.companyRepository = companyRepository;
        this.transactionService = transactionService;
    }

    // --------------------------------------
    // CREATE
    // --------------------------------------
    public BudgetResponseDTO createBudget(BudgetCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (headerRepo.findByCompanyIdAndFiscalYearAndIsActiveTrue(companyId, dto.getFiscalYear()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An active budget already exists for this fiscal year");
        }

        User user = User.builder()
                .id(auth.getCurrentUserId()).build();

        BudgetHeader header = BudgetHeader.builder()
                .budgetName(dto.getBudgetName())
                .fiscalYear(dto.getFiscalYear())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .amount(dto.getAmount())
                .status(BudgetStatus.IMPLEMENTED)
                .lines(new ArrayList<>())
                .company(company)
                .createdByUser(user)
                .build();

        BudgetHeader saved = headerRepo.save(header);

        return toDTO(saved);
    }

    // --------------------------------------
    // GET BY ID
    // --------------------------------------
    public BudgetResponseDTO getBudget(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (!header.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        return toDTO(header);
    }

    // --------------------------------------
    // LIST BY COMPANY
    // --------------------------------------
    public List<BudgetResponseDTO> listBudgets() {
        Long companyId = auth.getCurrentCompanyId();

        return headerRepo.findByCompanyId(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    // --------------------------------------
    // UPDATE (PUT): approved budgets → revise chain; others → new version row, original unchanged
    // --------------------------------------
    @Transactional
    public BudgetResponseDTO updateBudget(Long id, BudgetUpdateDTO dto) {
        BudgetHeader h = getBHEntity(id);
        if (h.getStatus() == BudgetStatus.APPROVED) {
            return reviseApprovedBudget(h, dto);
        }
        return editBudgetAsNewCopy(h, dto);
    }

    /**
     * Only {@link BudgetStatus#APPROVED} active budgets can be revised (new child under fiscal root).
     */
    private BudgetResponseDTO reviseApprovedBudget(BudgetHeader budget, BudgetUpdateDTO dto) {
        if (dto.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required to revise an approved budget");
        }
        if (!Boolean.TRUE.equals(budget.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the active budget can be revised");
        }
        if (budget.getAmount() != null && budget.getAmount().compareTo(dto.getAmount()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revised amount must differ from the current budget");
        }

        BudgetHeader root = budget.getParentBudget() == null ? budget : budget.getParentBudget();
        long reviseCount = headerRepo.countByParentBudgetId(root.getId());
        if (reviseCount >= 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exceeded maximum revise limit (5)");
        }

        User user = User.builder().id(auth.getCurrentUserId()).build();

        root.setReviseCount((root.getReviseCount() == null ? 0 : root.getReviseCount()) + 1);
        headerRepo.save(root);

        budget.setIsActive(false);
        headerRepo.save(budget);

        BudgetHeader newHeader = BudgetHeader.builder()
                .parentBudget(root)
                .status(BudgetStatus.REVISED)
                .budgetName(budget.getBudgetName() + " (Rev " + (reviseCount + 1) + ")")
                .fiscalYear(budget.getFiscalYear())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .amount(dto.getAmount())
                .reviseCount(reviseCount + 1)
                .isActive(true)
                .company(budget.getCompany())
                .createdByUser(user)
                .build();

        List<BudgetLine> copiedLines = copyLinesOntoHeader(budget, newHeader);
        newHeader.setLines(copiedLines);

        BudgetHeader saved = headerRepo.save(newHeader);
        headerRepo.deactivateOtherActivesForFiscalYear(
                saved.getCompany().getId(), saved.getFiscalYear(), saved.getId());
        return toDTO(headerRepo.findById(saved.getId()).orElseThrow());
    }

    /**
     * Non-approved budgets: create a new header with merged fields and copied lines; original row
     * keeps its status and is deactivated (one active budget per fiscal year).
     */
    private BudgetResponseDTO editBudgetAsNewCopy(BudgetHeader budget, BudgetUpdateDTO dto) {
        if (budget.getStatus() == BudgetStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use revise flow for approved budgets");
        }

        String newName = dto.getBudgetName() != null ? dto.getBudgetName() : budget.getBudgetName();
        String newFy = dto.getFiscalYear() != null ? dto.getFiscalYear() : budget.getFiscalYear();
        LocalDate newStart = dto.getStartDate() != null ? dto.getStartDate() : budget.getStartDate();
        LocalDate newEnd = dto.getEndDate() != null ? dto.getEndDate() : budget.getEndDate();
        BigDecimal newAmount = dto.getAmount() != null ? dto.getAmount() : budget.getAmount();

        if (Objects.equals(newName, budget.getBudgetName())
                && Objects.equals(newFy, budget.getFiscalYear())
                && Objects.equals(newStart, budget.getStartDate())
                && Objects.equals(newEnd, budget.getEndDate())
                && (budget.getAmount() == null && newAmount == null
                    || budget.getAmount() != null && newAmount != null
                        && budget.getAmount().compareTo(newAmount) == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No changes to apply");
        }

        User user = User.builder().id(auth.getCurrentUserId()).build();

        budget.setIsActive(false);
        headerRepo.save(budget);

        BudgetHeader fresh = BudgetHeader.builder()
                .budgetName(newName)
                .fiscalYear(newFy)
                .startDate(newStart)
                .endDate(newEnd)
                .amount(newAmount)
                .status(budget.getStatus())
                .lines(new ArrayList<>())
                .company(budget.getCompany())
                .createdByUser(user)
                .isActive(true)
                .parentBudget(null)
                .reviseCount(0L)
                .build();

        List<BudgetLine> copiedLines = copyLinesOntoHeader(budget, fresh);
        fresh.setLines(copiedLines);

        BudgetHeader saved = headerRepo.save(fresh);
        headerRepo.deactivateOtherActivesForFiscalYear(
                saved.getCompany().getId(), saved.getFiscalYear(), saved.getId());
        return toDTO(headerRepo.findById(saved.getId()).orElseThrow());
    }

    private List<BudgetLine> copyLinesOntoHeader(BudgetHeader source, BudgetHeader target) {
        List<BudgetLine> src = lineRepo.findByBudgetHeader_Id(source.getId());
        if (src.isEmpty()) {
            return new ArrayList<>();
        }
        return src.stream()
                .map(l -> BudgetLine.builder()
                        .budgetHeader(target)
                        .account(l.getAccount())
                        .department(l.getDepartment())
                        .projectId(l.getProjectId())
                        .startDate(l.getStartDate())
                        .endDate(l.getEndDate())
                        .notes(l.getNotes())
                        .status(BudgetStatus.IMPLEMENTED)
                        .createdByUser(l.getCreatedByUser())
                        .updatedByUser(null)
                        .approvedByUser(null)
                        .amount(l.getAmount())
                        .build())
                .toList();
    }

    // --------------------------------------
    // STATUS CHANGE
    // --------------------------------------
    public BudgetResponseDTO activate(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        if (!header.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        User user = User.builder()
                .id(auth.getCurrentUserId()).build();

        headerRepo.deactivateOtherActivesForFiscalYear(
                header.getCompany().getId(), header.getFiscalYear(), header.getId());

        header.setStatus(BudgetStatus.APPROVED);
        header.setIsActive(true);
        header.setApprovedByUser(user);

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO close(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus(BudgetStatus.REJECTED);
        header.setIsActive(false);

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO hold(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus(BudgetStatus.HOLD);

        return toDTO(headerRepo.save(header));
    }

    // --------------------------------------
    // DTO MAPPER
    // --------------------------------------
    private BudgetResponseDTO toDTO(BudgetHeader h) {

        return BudgetResponseDTO.builder()
                .id(h.getId())
                .budgetName(h.getBudgetName())
                .fiscalYear(h.getFiscalYear())
                .status(h.getStatus())
                .amount(h.getAmount())
                .isActive(Boolean.TRUE.equals(h.getIsActive()))
                .startDate(h.getStartDate())
                .endDate(h.getEndDate())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .companyId(h.getCompany().getId())
                .createdByUserId(h.getCreatedByUser() != null ? h.getCreatedByUser().getId() : null)
                .approvedByUserId(h.getApprovedByUser() != null ? h.getApprovedByUser().getId() : null)
                .lines(
                        h.getLines() != null ? h.getLines().stream().map(l ->
                                BudgetLineDTO.builder()
                                        .id(l.getId())
                                        .accountId(l.getAccount().getId())
                                        .accountName(l.getAccount() != null ? l.getAccount().getAccountName() : null)
                                        .accountCode(l.getAccount() != null ? l.getAccount().getAccountCode() : null)
                                        .departmentId(l.getDepartment() != null ? l.getDepartment().getId() : null)
                                        .departmentName(l.getDepartment() != null ? l.getDepartment().getDepartmentName() : null)
                                        .departmentCode(l.getDepartment() != null ? l.getDepartment().getDepartmentCode() : null)
                                        .status(l.getStatus())
                                        .projectId(l.getProjectId())
                                        .startDate(l.getStartDate())
                                        .endDate(l.getEndDate())
                                        .amount(l.getAmount())
                                        .notes(l.getNotes())
                                        .createdByUserName(l.getCreatedByUser() != null ? l.getCreatedByUser().getFullName() : null)
                                        .createdByUserId(l.getCreatedByUser() != null ? l.getCreatedByUser().getId() : null)
                                        .createdAt(l.getCreatedAt())
                                        .updatedAt(l.getUpdatedAt())
                                        .updatedByUserName(l.getUpdatedByUser() != null ? l.getUpdatedByUser().getFullName() : null)
                                        .updatedByUserId(l.getUpdatedByUser() != null ? l.getUpdatedByUser().getId() : null)
                                        .approvedByUserName(l.getApprovedByUser() != null ? l.getApprovedByUser().getFullName() : null)
                                        .approvedByUserId(l.getApprovedByUser() != null ? l.getApprovedByUser().getId() : null)

                                        .build()
                        ).toList() : new ArrayList<>()
                )
                .build();
    }


    @Transactional
    public BudgetResponseDTO addLine(Long journalEntryId, BudgetLineCreateDTO dto) {

        User user = User.builder()
                .id(auth.getCurrentUserId()).build();

        BudgetHeader bh = headerRepo.findById(journalEntryId)
                .orElseThrow(() -> new RuntimeException("Journal Entry not found"));

        if (bh.getStatus() != BudgetStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget is not approved");
        }

        // 3. Initialize lazy list to avoid transient issues
        List<BudgetLine> lines = bh.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            bh.setLines(lines);
        } else {
            lines.size(); // forces initialization
            // Validate budget limit
            validateBudgetLimit(bh, dto.getAmount());
        }

        // 4. Load managed Account (required)
        ChartOfAccounts account = accountRepo.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // 5. Load managed Department (optional)
        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = deptRepo.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        Optional<BudgetLine> existing =
                lineRepo.findByBudgetHeaderAndAccountAndDepartmentAndProjectId(
                        bh,
                        account,
                        dept,
                        dto.getProjectId()
                );


        if ((dto.getDepartmentId() != null || dto.getProjectId() != null) && existing.isPresent()) {
            BudgetLine bl = existing.get();
//            bh.setAmount(bh.getAmount() - bl.getAmount() + dto.getAmount());
            bl.setAmount(dto.getAmount());
            bl.setNotes(dto.getNotes());

        } else {
            BudgetLine line = BudgetLine.builder()
                    .budgetHeader(bh)
                    .account(account)
                    .department(dept)
                    .projectId(dto.getProjectId())
                    .notes(dto.getNotes())
                    .amount(dto.getAmount())
                    .startDate(dto.getStartDate())
                    .endDate(dto.getEndDate())
                    .createdByUser(user)
                    .status(BudgetStatus.IMPLEMENTED)
                    .build();

            bh.getLines().add(line);
//            bh.setAmount(bh.getAmount() + line.getAmount());

        }

        headerRepo.save(bh);

        // GL: posting runs when a line is first approved (updateLineStatus → APPROVED), not on addLine.

        return toDTO(bh);
    }

    private void validateBudgetLimit(BudgetHeader bh, BigDecimal newAmount) {

        BigDecimal totalAllocated = bh.getLines()
                .stream()
                .map(BudgetLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAfterAdd = totalAllocated.add(newAmount);

        if (totalAfterAdd.compareTo(bh.getAmount()) > 0) {
            throw new RuntimeException(
                    "Budget exceeded. Remaining budget: " +
                            bh.getAmount().subtract(totalAllocated)
            );
        }
    }


    @Transactional
    public BudgetResponseDTO updateLine(Long bhId, Long lineId, BudgetLineUpdateDTO dto) {
        User user = User.builder()
                .id(auth.getCurrentUserId()).build();
        BudgetHeader bh = getBHEntity(bhId);
        if (bh.getStatus() != BudgetStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Distributions can only be edited when the budget is approved");
        }

        BudgetLine line = lineRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Line not found"));

        ChartOfAccounts account = accountRepo.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = deptRepo.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        if (!line.getBudgetHeader().getId().equals(bhId))
            throw new RuntimeException("Line does not belong to this budget");

        BigDecimal prevAmount = line.getAmount();

        Optional<BudgetLine> existing =
                lineRepo.findByBudgetHeaderAndAccountAndDepartmentAndProjectId(
                        bh,
                        account,
                        dept,
                        dto.getProjectId()
                );

        if ((dto.getDepartmentId() != null || dto.getProjectId() != null) && existing.isPresent()) {
            BudgetLine bl = existing.get();
//            bh.setAmount(bh.getAmount() - (prevAmount + bl.getAmount()) + line.getAmount());
            bl.setAmount(dto.getAmount());
            bl.setNotes(dto.getNotes());
            bl.setUpdatedByUser(user);
            lineRepo.delete(line);
            lineRepo.save(bl);
        } else {
            line.setAccount(account);
            line.setDepartment(dto.getDepartmentId() != null ? dept : null);
            line.setProjectId(dto.getProjectId());
            line.setNotes(dto.getNotes());
            line.setAmount(dto.getAmount());
            line.setUpdatedByUser(user);
            line.setStartDate(dto.getStartDate());
            line.setEndDate(dto.getEndDate());
//            bh.setAmount(bh.getAmount() - prevAmount + line.getAmount());
            lineRepo.save(line);
        }
//        headerRepo.updateAmountOnly(bh.getId(), bh.getAmount());
        return toDTO(bh);
    }

    @Transactional
    public BudgetResponseDTO updateLineStatus(Long bhId, Long lineId, BudgetStatus status) {

        User user = User.builder()
                .id(auth.getCurrentUserId())
                .build();

        BudgetHeader bh = getBHEntity(bhId);
        if (bh.getStatus() != BudgetStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Line status can only be updated when the budget is approved");
        }

        BudgetLine line = lineRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Budget line not found"));

        if (!line.getBudgetHeader().getId().equals(bhId)) {
            throw new RuntimeException("Line does not belong to this budget");
        }

        BudgetStatus previous = line.getStatus();

        line.setStatus(status);

        if (status == BudgetStatus.APPROVED) {
            line.setApprovedByUser(user);
        }

        line.setUpdatedByUser(user);

        lineRepo.save(line);

        if (status == BudgetStatus.APPROVED
                && previous != BudgetStatus.APPROVED
                && line.getAmount() != null
                && line.getAmount().compareTo(BigDecimal.ZERO) != 0
                && !transactionService.hasBudgetPostingForLine(lineId)) {
            transactionService.recordBudgetLineApproved(line);
        }

        return toDTO(bh);
    }

    @Transactional
    public BudgetResponseDTO deleteLine(Long bhId, Long lineId) {
        BudgetHeader bh = getBHEntity(bhId);
        if (bh.getStatus() != BudgetStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Distributions can only be removed when the budget is approved");
        }

        BudgetLine line = lineRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Line not found"));

        if (!line.getBudgetHeader().getId().equals(bhId))
            throw new RuntimeException("Line does not belong to this budget");

        bh.getLines().remove(line);
//        bh.setAmount(bh.getAmount() - line.getAmount());
        lineRepo.delete(line);

//        recalcTotals(je);

        return toDTO(bh);
    }

    private BudgetHeader getBHEntity(Long id) {
        Long companyId = auth.getCurrentCompanyId();

        return headerRepo.findById(id)
                .filter(je -> je.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Budget Header not found or access denied"));
    }

//    public void isDistributionExceedsBudget(List<BudgetLine> lines, BudgetHeader bh) {
//        long totalLinesAmount = lines.stream()
//                .mapToLong(BudgetLine::getAmount)
//                .sum();
//
//        if (totalLinesAmount > bh.getAmount()) {
//            throw new RuntimeException("Total Budget distribution is greater than Total Budget");
//        }
//    }
}
