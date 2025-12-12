package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.BudgetHeader;
import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.BudgetCreateDTO;
import com.erp.dto.finance.BudgetLineDTO;
import com.erp.dto.finance.BudgetResponseDTO;
import com.erp.dto.finance.BudgetUpdateDTO;
import com.erp.repo.finance.BudgetHeaderRepository;
import com.erp.repo.finance.BudgetLineRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        BudgetHeader header = BudgetHeader.builder()
                .budgetName(dto.getBudgetName())
                .budgetYear(dto.getBudgetYear())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status("DRAFT")
                .company(Company.builder().id(companyId).build())
                .createdByUser(new User(auth.getCurrentUserId()))
                .build();

        List<BudgetLine> lines = dto.getLines().stream().map(l -> {
            ChartOfAccounts account = accountRepo.findById(l.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            Department department = null;
            if (l.getDepartmentId() != null) {
                department = deptRepo.findById(l.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Department not found"));
            }

            return BudgetLine.builder()
                    .budgetHeader(header)
                    .account(account)
                    .department(department)
                    .projectId(l.getProjectId())
                    .period(l.getPeriod())
                    .amount(l.getAmount())
                    .currencyCode(l.getCurrencyCode())
                    .notes(l.getNotes())
                    .build();
        }).toList();

        header.setLines(lines);

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

        if (!header.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        header.setBudgetName(dto.getBudgetName());
        header.setBudgetYear(dto.getBudgetYear());
        header.setStartDate(dto.getStartDate());
        header.setEndDate(dto.getEndDate());

        // Remove existing lines
        header.getLines().clear();
        lineRepo.deleteAll(lineRepo.findAll().stream()
                .filter(l -> l.getBudgetHeader().getId().equals(id))
                .toList());

        // Add new lines
        List<BudgetLine> newLines = dto.getLines().stream().map(l -> {
            ChartOfAccounts account = accountRepo.findById(l.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            Department department = null;
            if (l.getDepartmentId() != null) {
                department = deptRepo.findById(l.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Department not found"));
            }

            return BudgetLine.builder()
                    .budgetHeader(header)
                    .account(account)
                    .department(department)
                    .projectId(l.getProjectId())
                    .period(l.getPeriod())
                    .amount(l.getAmount())
                    .currencyCode(l.getCurrencyCode())
                    .notes(l.getNotes())
                    .build();
        }).toList();

        header.setLines(newLines);

        return toDTO(headerRepo.save(header));
    }

    // --------------------------------------
    // STATUS CHANGE
    // --------------------------------------
    public BudgetResponseDTO activate(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus("ACTIVE");
        header.setApprovedByUser(new User(auth.getCurrentUserId()));

        return toDTO(headerRepo.save(header));
    }

    public BudgetResponseDTO close(Long id) {
        BudgetHeader header = headerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        header.setStatus("CLOSED");

        return toDTO(headerRepo.save(header));
    }

    // --------------------------------------
    // DTO MAPPER
    // --------------------------------------
    private BudgetResponseDTO toDTO(BudgetHeader h) {
        return BudgetResponseDTO.builder()
                .id(h.getId())
                .budgetName(h.getBudgetName())
                .budgetYear(h.getBudgetYear())
                .status(h.getStatus())
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
                                        .departmentId(l.getDepartment() != null ? l.getDepartment().getId() : null)
                                        .projectId(l.getProjectId())
                                        .period(l.getPeriod())
                                        .amount(l.getAmount())
                                        .currencyCode(l.getCurrencyCode())
                                        .notes(l.getNotes())
                                        .build()
                        ).toList()
                )
                .build();
    }
}
