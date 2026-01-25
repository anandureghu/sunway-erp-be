package com.erp.service.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.GLAccountBalance;
import com.erp.domain.finance.Transaction;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.GLAccountBalanceRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository repo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;
    private final ChartOfAccountsRepository coaRepo;
    private final ChartOfAccountsService coaService;
    private final GLAccountBalanceRepository glRepo;

    public TransactionService(
            TransactionRepository repo,
            CompanyRepository companyRepo,
            AuthContext auth,
            ChartOfAccountsRepository coaRepo,
            GLAccountBalanceRepository glRepo,
            ChartOfAccountsService coaService
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.coaRepo = coaRepo;
        this.glRepo = glRepo;
        this.coaService = coaService;
    }

    @Transactional
    public TransactionResponseDTO create(CreateTransactionDTO dto) {

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts creditAccount = dto.getCreditAccount() != null ? coaRepo.findById(dto.getCreditAccount())
                .orElseThrow(() -> new RuntimeException("Invalid credit account")) : null;

        ChartOfAccounts debitAccount = dto.getDebitAccount() != null ? coaRepo.findById(dto.getDebitAccount())
                .orElseThrow(() -> new RuntimeException("Invalid debit account")) : null;

        if (creditAccount != null && dto.getRelatedId() == null) {
            coaService.updateBalance(creditAccount, dto.getAmount());
        }

        if (debitAccount != null && dto.getRelatedId() == null) {
            coaService.updateBalance(debitAccount, dto.getAmount().negate());
        }

        Transaction tx = Transaction.builder()
                .transactionCode("TX-" + System.currentTimeMillis())
                .transactionType(dto.getTransactionType())
//                .fiscalType(dto.getFiscalType())
                .company(company)
                .amount(dto.getAmount())
                .transactionDate(dto.getTransactionDate())
//                .debitAccount(dto.getDebitAccount())
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
//                .itemCode(dto.getItemCode())
                .invoiceId(dto.getInvoiceId())
                .paymentId(dto.getPaymentId())
                .transactionDescription(dto.getTransactionDescription())
                .createdBy(auth.getCurrentUserId())
                .relatedId(dto.getRelatedId())
                .relatedSubId(dto.getRelatedSubId())
//                .posted(false)
                .build();

        return toDTO(repo.save(tx));
    }

    public TransactionResponseDTO get(Long id) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return toDTO(tx);
    }


    public List<TransactionResponseDTO> listByCompany(Long companyId) {
        return repo.findByCompanyId(companyId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TransactionResponseDTO postTransaction(Long id, String fiscalYear) {

        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

//        if (tx.getPosted()) {
//            throw new RuntimeException("Transaction already posted");
//        }

        // Update GL Balances
//        updateBalance(tx.getDebitAccount(), fiscalYear, tx.getAmount(), true);
        updateBalance(tx.getCreditAccount().getId(), fiscalYear, tx.getAmount(), false);

//        tx.setPosted(true);
//        tx.setPostedDate(Instant.now());

        return toDTO(repo.save(tx));
    }

    private void updateBalance(Long accountId, String year, java.math.BigDecimal amount, boolean isDebit) {

        GLAccountBalance bal = glRepo.findByAccountIdAndFiscalYear(accountId, year)
                .orElse(GLAccountBalance.builder()
                        .accountId(accountId)
                        .fiscalYear(year)
                        .build());

        if (isDebit) {
            bal.setTotalAssets(bal.getTotalAssets().add(amount));
        } else {
            bal.setTotalRevenue(bal.getTotalRevenue().add(amount));
        }

        bal.setBalance(
                bal.getTotalAssets().subtract(bal.getTotalLiabilities())
                        .add(bal.getTotalRevenue())
                        .subtract(bal.getTotalExpenses())
        );

        glRepo.save(bal);
    }

    private TransactionResponseDTO toDTO(Transaction tx) {
        return TransactionResponseDTO.builder()
                .id(tx.getId())
                .transactionCode(tx.getTransactionCode())
                .transactionType(tx.getTransactionType())
//                .fiscalType(tx.getFiscalType())
                .transactionDate(tx.getTransactionDate())
//                .postedDate(tx.getPostedDate())
//                .posted(tx.getPosted())
                .amount(tx.getAmount())
//                .debitAccount(tx.getDebitAccount())
                .creditAccountId(tx.getCreditAccount().getId())
                .creditAccountName(tx.getCreditAccount().getAccountName())
                .debitAccountId(tx.getDebitAccount().getId())
                .debitAccountName(tx.getDebitAccount().getAccountName())
                .companyId(tx.getCompany().getId())
                .companyName(tx.getCompany().getCompanyName())
//                .itemCode(tx.getItemCode())
                .invoiceId(tx.getInvoiceId())
                .paymentId(tx.getPaymentId())
                .transactionDescription(tx.getTransactionDescription())
                .build();
    }

    @Transactional
    public Transaction createTransactionForPayment(Long paymentId,
                                                   Long companyId,
                                                   BigDecimal amount,
                                                   Long debitAccountId,
                                                   Long creditAccountId,
                                                   LocalDate txDate,
                                                   String txType) {
        ChartOfAccounts creditAccount = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid credit account"));


        ChartOfAccounts debitAccount = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid debit account"));

        Transaction tx = Transaction.builder()
                .transactionCode("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionType(txType)
                .company(null) // optional attach company later via repo lookup if needed
//                .fiscalType("DEFAULT")
                .transactionDate(txDate == null ? LocalDate.now() : txDate)
                .amount(amount)
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
//                .itemCode("PAYMENT#" + paymentId)
//                .posted(true) // mark posted immediately for payments
                .createdAt(Instant.now())
                .createdBy(null)
                .build();

        Transaction saved = repo.save(tx);

        // Update balances: debit account increases (for asset/expense) and credit account decreases (or increases for revenue/liability)
        updateGlAndCoaForPostedTransaction(saved);

        return saved;
    }

    private void updateGlAndCoaForPostedTransaction(Transaction tx) {
        var creditCoa = coaRepo.findById(tx.getCreditAccount().getId())
                .orElseThrow(() -> new RuntimeException("Credit COA not found: " + tx.getCreditAccount().getAccountCode()));
        var debitCoa = coaRepo.findById(tx.getDebitAccount().getId())
                .orElseThrow(() -> new RuntimeException("Debit COA not found: " + tx.getDebitAccount().getAccountCode()));

        // update ChartOfAccounts running balances (rule depends on type)
        coaAdjust(debitCoa, tx.getAmount(), true);
        coaAdjust(creditCoa, tx.getAmount(), false);

        // update GL account balances (fiscal year handling - use current year string)
        String fiscalYear = String.valueOf(java.time.Year.now().getValue());
        updateGLBalance(debitCoa.getId(), fiscalYear, tx.getAmount(), true);
        updateGLBalance(creditCoa.getId(), fiscalYear, tx.getAmount(), false);
    }

    private void coaAdjust(com.erp.domain.finance.ChartOfAccounts coa, java.math.BigDecimal amount, boolean isDebit) {
        String t = coa.getType() == null ? "asset" : coa.getType().toLowerCase();
        java.math.BigDecimal bal = coa.getBalance() == null ? java.math.BigDecimal.ZERO : coa.getBalance();
        switch (t) {
            case "asset":
            case "expense":
                // debit increases
                coa.setBalance(isDebit ? bal.add(amount) : bal.subtract(amount));
                break;
            case "liability":
            case "income":
            case "equity":
                // credit increases
                coa.setBalance(isDebit ? bal.subtract(amount) : bal.add(amount));
                break;
            default:
                coa.setBalance(isDebit ? bal.add(amount) : bal.subtract(amount));
        }
        coaRepo.save(coa);
    }

    private void updateGLBalance(Long accountId, String fiscalYear, java.math.BigDecimal amount, boolean isDebit) {
        GLAccountBalance bal = glRepo.findByAccountIdAndFiscalYear(accountId, fiscalYear)
                .orElse(GLAccountBalance.builder()
                        .accountId(accountId)
                        .fiscalYear(fiscalYear)
                        .asOfDate(Instant.now())
                        .totalAssets(java.math.BigDecimal.ZERO)
                        .totalExpenses(java.math.BigDecimal.ZERO)
                        .totalLiabilities(java.math.BigDecimal.ZERO)
                        .totalRevenue(java.math.BigDecimal.ZERO)
                        .balance(java.math.BigDecimal.ZERO)
                        .build());

        // Very simplified: treat asset/expense as debit-side; revenue/liability as credit-side
        var coa = coaRepo.findById(accountId).orElse(null);
        String type = coa != null && coa.getType() != null ? coa.getType().toLowerCase() : "asset";

        if ("asset".equals(type)) {
            if (isDebit) bal.setTotalAssets(bal.getTotalAssets().add(amount));
            else bal.setTotalAssets(bal.getTotalAssets().subtract(amount));
        } else if ("liability".equals(type)) {
            if (!isDebit) bal.setTotalLiabilities(bal.getTotalLiabilities().add(amount));
            else bal.setTotalLiabilities(bal.getTotalLiabilities().subtract(amount));
        } else if ("income".equals(type) || "revenue".equals(type)) {
            if (!isDebit) bal.setTotalRevenue(bal.getTotalRevenue().add(amount));
            else bal.setTotalRevenue(bal.getTotalRevenue().subtract(amount));
        } else if ("expense".equals(type)) {
            if (isDebit) bal.setTotalExpenses(bal.getTotalExpenses().add(amount));
            else bal.setTotalExpenses(bal.getTotalExpenses().subtract(amount));
        }

        // recompute balance
        bal.setBalance(
                bal.getTotalAssets()
                        .subtract(bal.getTotalLiabilities())
                        .add(bal.getTotalRevenue())
                        .subtract(bal.getTotalExpenses())
        );

        glRepo.save(bal);
    }
}
