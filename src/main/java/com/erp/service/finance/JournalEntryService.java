package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.JournalLine;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.finance.*;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.JournalEntryRepository;
import com.erp.repo.finance.JournalLineRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class JournalEntryService {

    private final JournalEntryRepository jeRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final DepartmentRepository deptRepo;
    private final AuthContext auth;
    private final JournalLineRepository jlRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;

    public JournalEntryService(
            JournalEntryRepository jeRepo,
            ChartOfAccountsRepository accountRepo,
            DepartmentRepository deptRepo,
            JournalLineRepository jlRepo,
            AuthContext auth,
            UserRepository userRepo,
            CompanyRepository companyRepo
    ) {
        this.jeRepo = jeRepo;
        this.accountRepo = accountRepo;
        this.deptRepo = deptRepo;
        this.jlRepo = jlRepo;
        this.auth = auth;
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
    }

    // --------------------------
    // Create Journal Entry
    // --------------------------
    public JournalEntryResponseDTO createJE(JournalEntryCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        // Validate debit == credit
        BigDecimal totalDebit = dto.getLines().stream()
                .map(l -> l.getDebitAmount() == null ? BigDecimal.ZERO : l.getDebitAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = dto.getLines().stream()
                .map(l -> l.getCreditAmount() == null ? BigDecimal.ZERO : l.getCreditAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new RuntimeException("Journal Entry is not balanced: Debit != Credit");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        User createdBy = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Build Journal Entry
        JournalEntry je = JournalEntry.builder()
                .journalEntryNumber(generateJENumber())
                .description(dto.getDescription())
                .entryDate(dto.getEntryDate())
                .source(dto.getSource())
                .periodId(dto.getPeriodId())
                .status("DRAFT")
                .company(company)
                .createdByUser(createdBy)
                .totalDebitAmount(totalDebit)
                .totalCreditAmount(totalCredit)
                .build();

        // Convert Line DTOs to Entities
        List<JournalLine> lines = dto.getLines().stream().map(line -> {
            ChartOfAccounts acct = accountRepo.findById(line.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            Department dept = null;
            if (line.getDepartmentId() != null) {
                dept = deptRepo.findById(line.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Department not found"));
            }

            return JournalLine.builder()
                    .journalEntry(je)
                    .account(acct)
                    .debitAmount(line.getDebitAmount())
                    .creditAmount(line.getCreditAmount())
                    .department(dept)
                    .projectId(line.getProjectId())
                    .currencyCode(line.getCurrencyCode())
                    .exchangeRate(line.getExchangeRate())
                    .description(line.getDescription())
                    .build();
        }).toList();

        je.setLines(lines);

        JournalEntry saved = jeRepo.save(je);
        return toDTO(saved);
    }

    // --------------------------
    // Post JE
    // --------------------------
    public JournalEntryResponseDTO postJE(Long id) {
        JournalEntry je = jeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("JE not found"));

        if (!je.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (!"DRAFT".equals(je.getStatus())) {
            throw new RuntimeException("Only DRAFT entries can be posted");
        }

        je.setStatus("POSTED");
        je.setPostedAt(Instant.now());
        je.setApprovedByUser(User.builder().id(auth.getCurrentUserId()).build());

        return toDTO(jeRepo.save(je));
    }

    // --------------------------
    // Reverse JE
    // --------------------------
    public JournalEntryResponseDTO reverseJE(Long id) {
        JournalEntry original = jeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("JE not found"));

        if (!"POSTED".equals(original.getStatus())) {
            throw new RuntimeException("Only POSTED entries can be reversed.");
        }

        User createdBy = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        User approvedBy = userRepo.findById(original.getApprovedByUser().getId())
                .orElseThrow(() -> new RuntimeException("Approved User not found"));

        // Create reversal entry
        JournalEntry reversal = JournalEntry.builder()
                .journalEntryNumber(generateJENumber())
                .entryDate(LocalDate.now())
                .description("Reversal of JE #" + original.getJournalEntryNumber())
                .status("POSTED")
                .source("SYSTEM")
                .company(original.getCompany())
                .createdByUser(createdBy)
                .approvedByUser(approvedBy)
                .postedAt(Instant.now())
                .build();

        List<JournalLine> reversedLines = original.getLines().stream().map(line ->
                JournalLine.builder()
                        .journalEntry(reversal)
                        .account(line.getAccount())
                        .debitAmount(line.getCreditAmount())   // Swap
                        .creditAmount(line.getDebitAmount())   // Swap
                        .department(line.getDepartment())
                        .projectId(line.getProjectId())
                        .currencyCode(line.getCurrencyCode())
                        .exchangeRate(line.getExchangeRate())
                        .description("Reversal of line " + line.getId())
                        .build()
        ).toList();

        reversal.setLines(reversedLines);
        reversal.setReversalEntryId(original.getId());

        // Mark original reversed
        original.setStatus("REVERSED");
        original.setReversedAt(Instant.now());
        original.setReversalEntryId(reversal.getId());

        jeRepo.save(original);
        JournalEntry savedReversal = jeRepo.save(reversal);

        return toDTO(savedReversal);
    }

    // --------------------------
    // Helpers
    // --------------------------

    private String generateJENumber() {
        return "JE-" + System.currentTimeMillis();
    }

    private JournalEntryResponseDTO toDTO(JournalEntry je) {
        return JournalEntryResponseDTO.builder()
                .id(je.getId())
                .journalEntryNumber(je.getJournalEntryNumber())
                .description(je.getDescription())
                .entryDate(je.getEntryDate())
                .periodId(je.getPeriodId())
                .status(je.getStatus())
                .source(je.getSource())
                .postedAt(je.getPostedAt())
                .reversedAt(je.getReversedAt())
                .reversalEntryId(je.getReversalEntryId())
                .totalDebit(je.getTotalDebitAmount())
                .totalCredit(je.getTotalCreditAmount())
                .lines(je.getLines().stream().map(l ->
                        JournalLineDTO.builder()
                                .id(l.getId())
                                .accountId(l.getAccount().getId())
                                .debitAmount(l.getDebitAmount())
                                .creditAmount(l.getCreditAmount())
                                .departmentId(l.getDepartment() != null ? l.getDepartment().getId() : null)
                                .projectId(l.getProjectId())
                                .currencyCode(l.getCurrencyCode())
                                .exchangeRate(l.getExchangeRate())
                                .description(l.getDescription())
                                .build()
                ).toList())
                .build();
    }

    public JournalEntryResponseDTO getJE(Long id) {
        JournalEntry je = jeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Journal Entry not found"));

        // Company check
        if (!je.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied: JE does not belong to your company");
        }

        return toDTO(je);
    }

    public List<JournalEntryResponseDTO> listForCompany() {
        Long companyId = auth.getCurrentCompanyId();

        return jeRepo.findByCompanyId(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public JournalEntryResponseDTO addLine(Long journalEntryId, JournalLineCreateDTO dto) {

        // 1. Load JE (managed entity)
        JournalEntry je = jeRepo.findById(journalEntryId)
                .orElseThrow(() -> new RuntimeException("Journal Entry not found"));

        // 2. Ensure JE belongs to the logged-in company
        if (!je.getCompany().getId().equals(auth.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        // 3. Initialize lazy list to avoid transient issues
        List<JournalLine> lines = je.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            je.setLines(lines);
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

        // 6. Create the new JournalLine (transient but fully attached)
        JournalLine line = JournalLine.builder()
                .journalEntry(je)
                .account(account)
                .department(dept)
                .debitAmount(dto.getDebitAmount())
                .creditAmount(dto.getCreditAmount())
                .projectId(dto.getProjectId())
                .currencyCode(dto.getCurrencyCode())
                .exchangeRate(dto.getExchangeRate())
                .description(dto.getDescription())
                .build();

        // 7. Attach line to JE (cascade handles persist!)
        lines.add(line);

        // 8. Persist JE immediately so Hibernate manages both JE + all lines
        je = jeRepo.saveAndFlush(je);

        // 9. Now safely recalc totals AFTER flush (avoids transient exceptions)
        recalcTotals(je);

        // 10. Save again after totals updated
        je = jeRepo.save(je);

        return toDTO(je);
    }


    @Transactional
    public JournalEntryResponseDTO updateLine(Long jeId, Long lineId, JournalLineUpdateDTO dto) {
        JournalEntry je = getJEEntity(jeId);

        JournalLine line = jlRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Line not found"));

        ChartOfAccounts account = accountRepo.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = deptRepo.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        if (!line.getJournalEntry().getId().equals(jeId))
            throw new RuntimeException("Line does not belong to this journal");

        line.setAccount(account);
        line.setDebitAmount(dto.getDebitAmount());
        line.setCreditAmount(dto.getCreditAmount());
        line.setDepartment(dto.getDepartmentId() != null ? dept : null);
        line.setProjectId(dto.getProjectId());
        line.setCurrencyCode(dto.getCurrencyCode());
        line.setExchangeRate(dto.getExchangeRate());
        line.setDescription(dto.getDescription());

        recalcTotals(je);

        jeRepo.save(je);
        return toDTO(je);
    }


    @Transactional
    public JournalEntryResponseDTO deleteLine(Long jeId, Long lineId) {
        JournalEntry je = getJEEntity(jeId);

        JournalLine line = jlRepo.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Line not found"));

        if (!line.getJournalEntry().getId().equals(jeId))
            throw new RuntimeException("Line does not belong to this journal");

        je.getLines().remove(line);
        jlRepo.delete(line);

        recalcTotals(je);

        return toDTO(je);
    }

    private JournalEntry getJEEntity(Long id) {
        Long companyId = auth.getCurrentCompanyId();

        return jeRepo.findById(id)
                .filter(je -> je.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Journal Entry not found or access denied"));
    }

    private void recalcTotals(JournalEntry je) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLine line : je.getLines()) {
            if (line.getDebitAmount() != null) {
                totalDebit = totalDebit.add(line.getDebitAmount());
            }
            if (line.getCreditAmount() != null) {
                totalCredit = totalCredit.add(line.getCreditAmount());
            }
        }

        je.setTotalDebitAmount(totalDebit);
        je.setTotalCreditAmount(totalCredit);
    }
}
