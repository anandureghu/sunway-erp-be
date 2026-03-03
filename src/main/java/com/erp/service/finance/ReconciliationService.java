package com.erp.service.finance;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Reconciliation;
import com.erp.domain.finance.ReconciliationStatus;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreateReconciliationRequest;
import com.erp.dto.finance.ReconciliationResponse;
import com.erp.dto.finance.UpdateReconciliationRequest;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.ReconciliationRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ReconciliationService {

    private final AuthContext authContext;
    private final ReconciliationRepository reconciliationRepo;
    private final ChartOfAccountsRepository accountRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    
    public Page<ReconciliationResponse> getAll(Pageable pageable) {

        Long companyId = authContext.getCurrentCompanyId();

        Page<Reconciliation> page =
                reconciliationRepo.findAllByCompanyId(companyId, pageable);

        return page.map(this::map);
    }

    public ReconciliationResponse create(CreateReconciliationRequest request) {

        Long userId = authContext.getCurrentUserId();
        Long companyId = authContext.getCurrentCompanyId();

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts account = accountRepo.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal initialBalance = account.getBalance();
        BigDecimal newBalance = initialBalance.add(request.getAmount());

        Reconciliation reconciliation = Reconciliation.builder()
                .company(company)
                .account(account)
                .amount(request.getAmount())
                .initialBalance(initialBalance)
                .newBalance(newBalance)
                .resource(request.getResource())
                .reason(request.getReason())
                .createdBy(user)
                .status(ReconciliationStatus.DRAFT)
                .build();

        reconciliationRepo.save(reconciliation);

        return map(reconciliation);
    }

    public ReconciliationResponse confirm(Long id) {

        Long userId = authContext.getCurrentUserId();
        Reconciliation rec = get(id);

        if (rec.getStatus() != ReconciliationStatus.DRAFT) {
            throw new RuntimeException("Only draft reconciliations can be confirmed");
        }

        ChartOfAccounts account = rec.getAccount();

        // Apply balance update
        account.setBalance(rec.getNewBalance());

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        rec.setStatus(ReconciliationStatus.CONFIRMED);
        rec.setConfirmedBy(user);
        rec.setConfirmedAt(LocalDateTime.now());

        return map(rec);
    }

    private Reconciliation get(Long id) {
        Long companyId = authContext.getCurrentCompanyId();

        return reconciliationRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Reconciliation not found"));
    }

    // ============================
// EDIT
// ============================
    public ReconciliationResponse edit(Long id, UpdateReconciliationRequest request) {

        Reconciliation rec = get(id);

        Long userId = authContext.getCurrentUserId();
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (rec.getStatus() == ReconciliationStatus.CONFIRMED) {
            throw new RuntimeException("Confirmed reconciliation cannot be edited");
        }

        ChartOfAccounts account = accountRepo.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        BigDecimal initialBalance = account.getBalance();
        BigDecimal newBalance = initialBalance.add(request.getAmount());

        rec.setAccount(account);
        rec.setAmount(request.getAmount());
        rec.setInitialBalance(initialBalance);
        rec.setNewBalance(newBalance);
        rec.setResource(request.getResource());
        rec.setUpdatedBy(user);
        rec.setReason(request.getReason());

        return map(rec);
    }

    private ReconciliationResponse map(Reconciliation r) {
        return ReconciliationResponse.builder()
                .id(r.getId())
                .accountId(r.getAccount().getId())
                .accountCode(r.getAccount().getAccountCode())
                .accountName(r.getAccount().getAccountName())
                .amount(r.getAmount())
                .initialBalance(r.getInitialBalance())
                .newBalance(r.getNewBalance())
                .resource(r.getResource())
                .reason(r.getReason())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .confirmedAt(r.getConfirmedAt())
                .createdById(r.getCreatedBy() != null ? r.getCreatedBy().getId() : null)
                .createdByName(r.getCreatedBy() != null ? r.getCreatedBy().getFullName() : null)
                .updatedById(r.getUpdatedBy() != null ? r.getUpdatedBy().getId() : null)
                .updatedByName(r.getUpdatedBy() != null ? r.getUpdatedBy().getFullName() : null)
                .confirmedById(r.getConfirmedBy() != null ? r.getConfirmedBy().getId() : null)
                .confirmedByName(r.getConfirmedBy() != null ? r.getConfirmedBy().getFullName() : null)
                .build();
    }
}