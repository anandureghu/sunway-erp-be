package com.erp.service.finance;

import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.dto.hr.ProcessAccountPair;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.service.hr.ProcessAccountDefaultsService;
import com.erp.service.purchase.PurchasePostingAccountsResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

/**
 * Resolves GL accounts strictly from company default-account configuration
 * (Finance → Default accounts). Does not scan the chart of accounts or guess by type.
 */
@Service
public class CompanyAccountingDefaultsService {

    private final CompanyRepository companyRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final PurchasePostingAccountsResolver purchasePostingAccountsResolver;
    private final ProcessAccountDefaultsService processAccountDefaultsService;

    public CompanyAccountingDefaultsService(
            CompanyRepository companyRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            PurchasePostingAccountsResolver purchasePostingAccountsResolver,
            ProcessAccountDefaultsService processAccountDefaultsService) {
        this.companyRepository = companyRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.purchasePostingAccountsResolver = purchasePostingAccountsResolver;
        this.processAccountDefaultsService = processAccountDefaultsService;
    }

    /**
     * Asset / cash GL account configured as the sales debit default (receipts and disbursements).
     */
    public Long requireCashGlAccountId(Long companyId) {
        return requireConfiguredCoa(
                companyId,
                requireCompany(companyId).getDefaultSalesDebitAccountId(),
                "Configure the sales debit account under Finance → Default accounts (used as the cash GL account for payments).");
    }

    public Long requireSalesDebitAccountId(Long companyId) {
        return requireCashGlAccountId(companyId);
    }

    public Long requireSalesCreditAccountId(Long companyId) {
        return requireConfiguredCoa(
                companyId,
                requireCompany(companyId).getDefaultSalesCreditAccountId(),
                "Configure the sales credit account under Finance → Default accounts.");
    }

    public PurchasePostingAccountsResolver.ResolvedAccounts requirePurchaseAccounts(Long companyId) {
        return purchasePostingAccountsResolver.resolve(companyId, null, null);
    }

    /**
     * @deprecated for GL posting — use {@link #requirePurchaseAccounts(Long)} so only Finance → Default accounts apply.
     */
    public PurchasePostingAccountsResolver.ResolvedAccounts resolvePurchaseAccounts(
            Long companyId, Long debitAccountId, Long creditAccountId) {
        return purchasePostingAccountsResolver.resolve(companyId, debitAccountId, creditAccountId);
    }

    public ProcessAccountPair requireProcessAccounts(
            Long companyId, AccountingProcessCode processCode) {
        return processAccountDefaultsService.resolveProcessDefaults(companyId, processCode)
                .filter(ProcessAccountPair::isComplete)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Configure "
                                + processCode
                                + " debit and credit accounts under Finance → Default accounts → Process account defaults."));
    }

    public void assertDistinctAccounts(String context, Long... accountIds) {
        Set<Long> seen = new HashSet<>();
        for (Long accountId : accountIds) {
            if (accountId == null) {
                continue;
            }
            if (!seen.add(accountId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        context + ": debit and credit accounts cannot be the same. "
                                + "Use separate accounts in Finance → Default accounts.");
            }
        }
    }

    private Company requireCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private Long requireConfiguredCoa(Long companyId, Long accountId, String message) {
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        ChartOfAccounts account = chartOfAccountsRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Configured default account not found in chart of accounts"));
        if (!account.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Configured default account does not belong to this company");
        }
        return accountId;
    }
}
