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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public BudgetService(
            BudgetHeaderRepository headerRepo,
            BudgetLineRepository lineRepo,
            ChartOfAccountsRepository accountRepo,
            DepartmentRepository deptRepo,
            AuthContext auth,
            CompanyRepository companyRepository
    ) {
        this.headerRepo = headerRepo;
        this.lineRepo = lineRepo;
        this.accountRepo = accountRepo;
        this.deptRepo = deptRepo;
        this.auth = auth;
        this.companyRepository = companyRepository;
    }

    // --------------------------------------
    // CREATE
    // --------------------------------------
    public BudgetResponseDTO createBudget(BudgetCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

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
    // UPDATE
    // --------------------------------------
    @Transactional
    public BudgetResponseDTO revise(Long id, BudgetUpdateDTO dto) {

        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (Objects.equals(header.getAmount(), dto.getAmount())) {
            throw new RuntimeException("revised amount is same as budget");
        }

        // Always revise from root budget
        if (header.getParentBudget() != null) {
            header = header.getParentBudget();
        }

        long reviseCount = headerRepo.countByParentBudgetId(header.getId());

        if (reviseCount >= 5) {
            throw new RuntimeException("Exceeded maximum revise limit (5)");
        }

        // update parent revision count
        header.setReviseCount((header.getReviseCount() == null ? 0 : header.getReviseCount()) + 1);
        headerRepo.save(header);

        BudgetHeader newHeader = BudgetHeader.builder()
                .parentBudget(header)
                .status(BudgetStatus.REVISED)
                .budgetName(header.getBudgetName() + " (Rev " + (reviseCount + 1) + ")")
                .fiscalYear(header.getFiscalYear())
                .startDate(header.getStartDate())
                .endDate(header.getEndDate())
                .amount(dto.getAmount())
                .reviseCount(reviseCount + 1)
                .company(header.getCompany())
                .build();

        newHeader = headerRepo.save(newHeader);

        BudgetHeader finalNewHeader = newHeader;
        List<BudgetLine> copiedLines = header.getLines() == null || header.getLines().isEmpty()
                ? new ArrayList<>()
                : header.getLines().stream()
                .map(l -> BudgetLine.builder()
                        .account(l.getAccount())
                        .department(l.getDepartment())
                        .projectId(l.getProjectId())
                        .startDate(l.getStartDate())
                        .endDate(l.getEndDate())
                        .notes(l.getNotes())
                        .status(BudgetStatus.IMPLEMENTED)
                        .createdByUser(l.getCreatedByUser())
                        .updatedByUser(l.getUpdatedByUser())
                        .approvedByUser(l.getApprovedByUser())
                        .amount(l.getAmount())
                        .budgetHeader(finalNewHeader)
                        .build())
                .toList();

        newHeader.setLines(copiedLines);

        return toDTO(headerRepo.save(newHeader));
    }

    // --------------------------------------
    // STATUS CHANGE
    // --------------------------------------
    public BudgetResponseDTO activate(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        User user = User.builder()
                .id(auth.getCurrentUserId()).build();

        header.setStatus(BudgetStatus.APPROVED);
        header.setApprovedByUser(user);

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO close(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus(BudgetStatus.REJECTED);

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
            throw new RuntimeException("Budget is not approved");
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

        // TODO: do this on confirmation
//        transactionService.create(CreateTransactionDTO.builder()
//                .companyId(auth.getCurrentCompanyId())
//                .transactionType("BUDGET")
//                .transactionDate(LocalDate.now())
//                .amount(dto.getAmount())
//                .relatedId(bh.getId())
//                .relatedSubId(affectedLine.getId())
//                .debitAccount(account.getId())
//                .creditAccount(account.getId())
//                .transactionDescription(
//                        StringUtils.defaultIfBlank(
//                                dto.getNotes(),
//                                "Budget allocation"
//                        )
//                ).build());


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

        BudgetLine line = lineRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Budget line not found"));

        if (!line.getBudgetHeader().getId().equals(bhId)) {
            throw new RuntimeException("Line does not belong to this budget");
        }

        line.setStatus(status);

        if (status == BudgetStatus.APPROVED) {
            line.setApprovedByUser(user);
        }

        line.setUpdatedByUser(user);

        lineRepo.save(line);

        return toDTO(bh);
    }

    @Transactional
    public BudgetResponseDTO deleteLine(Long bhId, Long lineId) {
        BudgetHeader bh = getBHEntity(bhId);

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
