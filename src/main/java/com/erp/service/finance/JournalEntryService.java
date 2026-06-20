package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.JournalEntryStatus;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreateJournalEntryRequest;
import com.erp.dto.finance.JournalEntryResponse;
import com.erp.dto.finance.UpdateJournalEntryRequest;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.JournalEntryRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class JournalEntryService {

    private final AuthContext authContext;
    private final JournalEntryRepository journalRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final TransactionService transactionService;


    public Page<JournalEntryResponse> getAll(Pageable pageable, Boolean archived) {

        Long companyId = authContext.getCurrentCompanyId();
        boolean showArchived = Boolean.TRUE.equals(archived);

        Page<JournalEntry> page =
                journalRepo.findAllByCompanyIdAndArchived(companyId, showArchived, pageable);

        return page.map(this::map);
    }

    // ============================
    // CREATE
    // ============================
    public JournalEntryResponse create(CreateJournalEntryRequest request) {

        Long userId = authContext.getCurrentUserId();

        Long companyId = authContext.getCurrentCompanyId();

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (request.getCreditAccountId().equals(request.getDebitAccountId())) {
            throw new RuntimeException("Debit and Credit accounts cannot be same");
        }

        ChartOfAccounts credit = accountRepo.findById(request.getCreditAccountId())
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        ChartOfAccounts debit = accountRepo.findById(request.getDebitAccountId())
                .orElseThrow(() -> new RuntimeException("Debit account not found"));

        assertCoaBelongsToCompany(credit, companyId);
        assertCoaBelongsToCompany(debit, companyId);

        User creator = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        JournalEntry entry = JournalEntry.builder()
                .company(company)
                .creditAccount(credit)
                .debitAccount(debit)
                .amount(request.getAmount())
                .source(request.getSource())
                .description(request.getDescription())
                .createdBy(creator)
                .status(JournalEntryStatus.PENDING_APPROVAL)
                .build();

        journalRepo.saveAndFlush(entry);
        entry.setJeNumber("JE-" + String.format("%07d", entry.getId()));

        return map(entry);
    }

    // ============================
    // APPROVE
    // ============================
    public JournalEntryResponse approve(Long id) {

        JournalEntry entry = getEntry(id);
        assertNotArchived(entry);
        Long approverId = authContext.getCurrentUserId();

        if (entry.getStatus() == JournalEntryStatus.APPROVED) {
            throw new RuntimeException("Journal entry already approved");
        }
        if (entry.getStatus() == JournalEntryStatus.REJECTED) {
            throw new RuntimeException("Cannot approve a rejected journal entry");
        }

        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        entry.setStatus(JournalEntryStatus.APPROVED);
        entry.setApprovedBy(approver);
        entry.setApprovedAt(LocalDateTime.now());
        entry.setUpdatedBy(approver);

        journalRepo.save(entry);

        // Finance transaction + COA posting only on approval (not on create)
        transactionService.createForJournalEntryApproval(entry);

        return map(entry);
    }

    // ============================
    // REJECT
    // ============================
    public JournalEntryResponse reject(Long id) {

        JournalEntry entry = getEntry(id);
        assertNotArchived(entry);
        Long userId = authContext.getCurrentUserId();

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        entry.setStatus(JournalEntryStatus.REJECTED);
        entry.setUpdatedBy(user);

        return map(entry);
    }

    // ============================
    // HOLD
    // ============================
    public JournalEntryResponse hold(Long id) {

        Long userId = authContext.getCurrentUserId();
        JournalEntry entry = getEntry(id);
        assertNotArchived(entry);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        entry.setStatus(JournalEntryStatus.ON_HOLD);
        entry.setUpdatedBy(user);

        return map(entry);
    }

    // ============================
// EDIT
// ============================
    public JournalEntryResponse edit(Long id, UpdateJournalEntryRequest request) {

        Long userId = authContext.getCurrentUserId();
        JournalEntry entry = getEntry(id);
        assertNotArchived(entry);

        if (request.getCreditAccountId().equals(request.getDebitAccountId())) {
            throw new RuntimeException("Debit and Credit accounts cannot be same");
        }

        ChartOfAccounts credit = accountRepo.findById(request.getCreditAccountId())
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        ChartOfAccounts debit = accountRepo.findById(request.getDebitAccountId())
                .orElseThrow(() -> new RuntimeException("Debit account not found"));

        Long companyId = authContext.getCurrentCompanyId();
        assertCoaBelongsToCompany(credit, companyId);
        assertCoaBelongsToCompany(debit, companyId);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        entry.setCreditAccount(credit);
        entry.setDebitAccount(debit);
        entry.setAmount(request.getAmount());
        entry.setSource(request.getSource());
        entry.setDescription(request.getDescription());

        // If it was ON_HOLD, move back to pending
        if (entry.getStatus() == JournalEntryStatus.ON_HOLD) {
            entry.setStatus(JournalEntryStatus.PENDING_APPROVAL);
        }

        entry.setUpdatedBy(user);

        return map(entry);
    }

    // ============================
    // ARCHIVE
    // ============================
    public JournalEntryResponse archive(Long id) {
        JournalEntry entry = getEntry(id);
        Long userId = authContext.getCurrentUserId();

        if (Boolean.TRUE.equals(entry.getArchived())) {
            return map(entry);
        }
        if (entry.getStatus() == JournalEntryStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Pending journal entries must be approved, rejected, or held before archiving");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        entry.setArchived(true);
        entry.setArchivedAt(LocalDateTime.now());
        entry.setArchivedBy(user);
        entry.setUpdatedBy(user);

        return map(journalRepo.save(entry));
    }

    // ============================
    // HELPER
    // ============================
    private void assertCoaBelongsToCompany(ChartOfAccounts coa, Long companyId) {
        if (coa.getCompany() == null || companyId == null
                || !coa.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Account does not belong to your company");
        }
    }

    private JournalEntry getEntry(Long id) {
        Long companyId = authContext.getCurrentCompanyId();

        return journalRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Journal Entry not found"));
    }

    private void assertNotArchived(JournalEntry entry) {
        if (Boolean.TRUE.equals(entry.getArchived())) {
            throw new RuntimeException("Archived journal entries cannot be modified");
        }
    }

    private JournalEntryResponse map(JournalEntry e) {
        return JournalEntryResponse.builder()
                .id(e.getId())
                .jeNumber(e.getJeNumber())
                .creditAccountId(e.getCreditAccount() != null ? e.getCreditAccount().getId() : null)
                .creditAccountName(e.getCreditAccount() != null ? e.getCreditAccount().getAccountName() : null)
                .creditAccountCode(e.getCreditAccount() != null ? e.getCreditAccount().getAccountCode() : null)
                .debitAccountId(e.getDebitAccount() != null ? e.getDebitAccount().getId() : null)
                .debitAccountName(e.getDebitAccount() != null ? e.getDebitAccount().getAccountName() : null)
                .debitAccountCode(e.getDebitAccount() != null ? e.getDebitAccount().getAccountCode() : null)
                .amount(e.getAmount())
                .source(e.getSource())
                .description(e.getDescription())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .approvedAt(e.getApprovedAt())
                .createdById(e.getCreatedBy() != null ? e.getCreatedBy().getId() : null)
                .createdByName(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : null)
                .updatedById(e.getUpdatedBy() != null ? e.getUpdatedBy().getId() : null)
                .updatedByName(e.getUpdatedBy() != null ? e.getUpdatedBy().getFullName() : null)
                .approvedById(e.getApprovedBy() != null ? e.getApprovedBy().getId() : null)
                .approvedByName(e.getApprovedBy() != null ? e.getApprovedBy().getFullName() : null)
                .archived(Boolean.TRUE.equals(e.getArchived()))
                .archivedAt(e.getArchivedAt())
                .archivedById(e.getArchivedBy() != null ? e.getArchivedBy().getId() : null)
                .archivedByName(e.getArchivedBy() != null ? e.getArchivedBy().getFullName() : null)
                .build();
    }
}