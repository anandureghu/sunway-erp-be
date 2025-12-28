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
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BudgetService {

    private final BudgetHeaderRepository headerRepo;
    private final BudgetLineRepository lineRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final DepartmentRepository deptRepo;
    private final AuthContext auth;

    public BudgetService(
            BudgetHeaderRepository headerRepo,
            BudgetLineRepository lineRepo,
            ChartOfAccountsRepository accountRepo,
            DepartmentRepository deptRepo,
            AuthContext auth
    ) {
        this.headerRepo = headerRepo;
        this.lineRepo = lineRepo;
        this.accountRepo = accountRepo;
        this.deptRepo = deptRepo;
        this.auth = auth;
    }

    // --------------------------------------
    // CREATE
    // --------------------------------------
    public BudgetResponseDTO createBudget(BudgetCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Department dept = null;
        if (dto.getDepartment() != null) {
            dept = deptRepo.findById(dto.getDepartment())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }
        BudgetHeader header = BudgetHeader.builder()
                .budgetName(dto.getBudgetName())
                .budgetYear(dto.getBudgetYear())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .amount(dto.getAmount())
                .status(BudgetStatus.IMPLEMENTED)
                .lines(new ArrayList<>())
                .department(dept)
                .projectId(dto.getProjectId())
                .company(Company.builder().id(companyId).build())
                .createdByUser(new User(auth.getCurrentUserId()))
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
    public BudgetResponseDTO updateBudget(Long id, BudgetUpdateDTO dto) {

        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));


        Department dept = null;
        if (dto.getDepartment() != null) {
            dept = deptRepo.findById(dto.getDepartment())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        if (!header.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        header.setBudgetName(dto.getBudgetName());
        header.setBudgetYear(dto.getBudgetYear());
        header.setStartDate(dto.getStartDate());
        header.setEndDate(dto.getEndDate());
        header.setAmount(dto.getAmount());
        header.setDepartment(dept);
        header.setProjectId(dto.getProjectId());

        return toDTO(headerRepo.save(header));
    }

    // --------------------------------------
    // STATUS CHANGE
    // --------------------------------------
    public BudgetResponseDTO activate(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus(BudgetStatus.APPROVED);
        header.setApprovedByUser(new User(auth.getCurrentUserId()));

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO close(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus(BudgetStatus.CLOSED);

        return toDTO(headerRepo.save(header));
    }

    // --------------------------------------
    // DTO MAPPER
    // --------------------------------------
    private BudgetResponseDTO toDTO(BudgetHeader h) {
        long totalLinesAmount = h.getLines().stream()
                .mapToLong(BudgetLine::getAmount)
                .sum();
        return BudgetResponseDTO.builder()
                .id(h.getId())
                .budgetName(h.getBudgetName())
                .budgetYear(h.getBudgetYear())
                .status(h.getStatus())
                .amount(h.getAmount())
                .balance(h.getAmount() - totalLinesAmount)
                .startDate(h.getStartDate())
                .endDate(h.getEndDate())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .companyId(h.getCompany().getId())
                .createdByUserId(h.getCreatedByUser() != null ? h.getCreatedByUser().getId() : null)
                .approvedByUserId(h.getApprovedByUser() != null ? h.getApprovedByUser().getId() : null)
                .lines(
                        h.getLines().stream().map(l ->
                                BudgetLineDTO.builder()
                                        .id(l.getId())
                                        .accountId(l.getAccount().getId())
                                        .accountName(l.getAccount().getAccountName())
                                        .departmentId(l.getDepartment() != null ? l.getDepartment().getId() : null)
                                        .projectId(l.getProjectId())
                                        .startDate(l.getStartDate())
                                        .endDate(l.getEndDate())
                                        .amount(l.getAmount())
                                        .notes(l.getNotes())
                                        .build()
                        ).toList()
                )
                .build();
    }


    @Transactional
    public BudgetResponseDTO addLine(Long journalEntryId, BudgetLineCreateDTO dto) {

        BudgetHeader bh = headerRepo.findById(journalEntryId)
                .orElseThrow(() -> new RuntimeException("Journal Entry not found"));

        // 2. Ensure JE belongs to the logged-in company
        if (!bh.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        // 3. Initialize lazy list to avoid transient issues
        List<BudgetLine> lines = bh.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            bh.setLines(lines);
        } else {
            lines.size(); // forces initialization
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

        isDistributionExceedsBudget(lines, bh);

        // 6. Create the new JournalLine (transient but fully attached)
        BudgetLine line = BudgetLine.builder()
                .budgetHeader(bh)
                .account(account)
                .department(dept)
                .projectId(dto.getProjectId())
                .notes(dto.getNotes())
                .amount(dto.getAmount())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .build();

        // 7. Attach line to JE (cascade handles persist!)
        lines.add(line);

        // 8. Persist JE immediately so Hibernate manages both JE + all lines
        bh = headerRepo.saveAndFlush(bh);

        // 9. Now safely recalc totals AFTER flush (avoids transient exceptions)
//        recalcTotals(je);

        // 10. Save again after totals updated
        bh = headerRepo.save(bh);

        return toDTO(bh);
    }


    @Transactional
    public BudgetResponseDTO updateLine(Long bhId, Long lineId, BudgetLineUpdateDTO dto) {
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

        line.setAccount(account);
        line.setDepartment(dto.getDepartmentId() != null ? dept : null);
        line.setProjectId(dto.getProjectId());
        line.setNotes(dto.getNotes());
        line.setAmount(dto.getAmount());
        line.setStartDate(dto.getStartDate());
        line.setEndDate(dto.getEndDate());

//        recalcTotals(je);

        isDistributionExceedsBudget(bh.getLines(), bh);

        headerRepo.save(bh);
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

    public void isDistributionExceedsBudget(List<BudgetLine> lines, BudgetHeader bh) {
        long totalLinesAmount = lines.stream()
                .mapToLong(BudgetLine::getAmount)
                .sum();

        if (totalLinesAmount > bh.getAmount()) {
            throw new RuntimeException("Total Budget distribution is greater than Total Budget");
        }
    }
}
