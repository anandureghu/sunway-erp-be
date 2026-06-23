package com.erp.service.purchase;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.CompanyRepository;
import org.springframework.stereotype.Service;

@Service
public class PurchasePostingAccountsResolver {

    private final CompanyRepository companyRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;

    public PurchasePostingAccountsResolver(
            CompanyRepository companyRepository,
            ChartOfAccountsRepository chartOfAccountsRepository) {
        this.companyRepository = companyRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
    }

    public record ResolvedAccounts(
            Long debitAccountId,
            Long creditAccountId,
            ChartOfAccounts debitAccount,
            ChartOfAccounts creditAccount
    ) {}

    /**
     * Resolves purchase GL legs from explicit ids or company purchase defaults.
     */
    public ResolvedAccounts resolve(Long companyId, Long debitAccountId, Long creditAccountId) {
        Long debitId = debitAccountId;
        Long creditId = creditAccountId;

        if (debitId == null || creditId == null) {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("Company not found"));
            if (debitId == null) {
                debitId = company.getDefaultPurchaseDebitAccountId();
            }
            if (creditId == null) {
                creditId = company.getDefaultPurchaseCreditAccountId();
            }
        }

        if (debitId == null || creditId == null) {
            throw new RuntimeException(
                    "Configure default purchase debit and credit accounts under Global Settings → Default Accounts.");
        }
        if (debitId.equals(creditId)) {
            throw new RuntimeException("Debit and credit accounts cannot be the same");
        }

        ChartOfAccounts debit = chartOfAccountsRepository.findById(debitId)
                .orElseThrow(() -> new RuntimeException("Purchase debit account not found"));
        ChartOfAccounts credit = chartOfAccountsRepository.findById(creditId)
                .orElseThrow(() -> new RuntimeException("Purchase credit account not found"));

        if (!debit.getCompany().getId().equals(companyId)
                || !credit.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Purchase accounts do not belong to this company");
        }

        return new ResolvedAccounts(debitId, creditId, debit, credit);
    }
}
