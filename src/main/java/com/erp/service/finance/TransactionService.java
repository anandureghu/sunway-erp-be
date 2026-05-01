package com.erp.service.finance;

import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.GLAccountBalance;
import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.Reconciliation;
import com.erp.domain.finance.Transaction;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.finance.UpdateTransactionDTO;
import com.erp.dto.finance.UpdateTransactionSourceDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.GLAccountBalanceRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    public static final String SOURCE_UNKNOWN = "UNKNOWN";
    public static final String TYPE_OPENING_BALANCE = "OPENING_BALANCE";
    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_JOURNAL = "JOURNAL";
    public static final String TYPE_RECONCILIATION = "RECONCILIATION";
    public static final String TYPE_BUDGET = "BUDGET";
    /** Two-sided COA posting when a purchase requisition is approved to a PO. */
    public static final String TYPE_PURCHASE_REQUISITION = "PURCHASE_REQUISITION";
    /** Vendor payment confirmed in AP: reduces AP and cash (custom posting, not {@link #applyPostingToCoa}). */
    public static final String TYPE_VENDOR_PAYMENT = "VENDOR_PAYMENT";
    public static final String TYPE_SALES_ORDER_CANCEL_REVERSAL = "SALES_ORDER_CANCEL_REVERSAL";

    private final TransactionRepository repo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;
    private final ChartOfAccountsRepository coaRepo;
    private final GLAccountBalanceRepository glRepo;

    public TransactionService(
            TransactionRepository repo,
            CompanyRepository companyRepo,
            AuthContext auth,
            ChartOfAccountsRepository coaRepo,
            GLAccountBalanceRepository glRepo
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.coaRepo = coaRepo;
        this.glRepo = glRepo;
    }

    /**
     * Opening balance: credit-only leg; updates COA balance via posting. Caller should set
     * {@code initialBalanceSet} on the account after this returns.
     */
    @Transactional
    public TransactionResponseDTO recordOpeningBalanceCreditOnly(Long creditAccountId, BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }
        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts credit = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!credit.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to your company");
        }

        Transaction tx = Transaction.builder()
                .transactionCode("TX-" + System.currentTimeMillis())
                .transactionType(TYPE_OPENING_BALANCE)
                .company(company)
                .amount(amount)
                .transactionDate(LocalDate.now())
                .creditAccount(credit)
                .debitAccount(null)
                .source(SOURCE_UNKNOWN)
                .sourceLocked(false)
                .transactionDescription("Opening balance")
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(repo.findById(saved.getId()).orElseThrow());
    }

    /**
     * Called when a manual journal entry is approved: creates a finance {@link Transaction} and
     * posts to COA balances (replaces previous inline balance updates on approve).
     */
    @Transactional
    public TransactionResponseDTO createForJournalEntryApproval(JournalEntry entry) {
        if (entry.getDebitAccount() == null || entry.getCreditAccount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal entry must have debit and credit accounts");
        }
        LocalDate txDate = entry.getApprovedAt() != null
                ? entry.getApprovedAt().toLocalDate()
                : LocalDate.now();

        Transaction tx = Transaction.builder()
                .transactionCode("TX-JE-" + entry.getId())
                .transactionType(TYPE_JOURNAL)
                .company(entry.getCompany())
                .amount(entry.getAmount())
                .transactionDate(txDate)
                .creditAccount(entry.getCreditAccount())
                .debitAccount(entry.getDebitAccount())
                .source(null)
                .sourceLocked(false)
                .transactionDescription(entry.getDescription())
                .relatedId(entry.getId())
                .relatedSubId(null)
                .createdAt(Instant.now())
                .createdBy(entry.getApprovedBy() != null ? entry.getApprovedBy().getId() : null)
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(repo.findById(saved.getId()).orElseThrow());
    }

    /**
     * Single COA leg: positive {@code signedDelta} credits the account; negative debits it.
     * Used for reconciliation and budget line postings (single-sided, source UNKNOWN unless provided).
     */
    @Transactional
    public TransactionResponseDTO recordSingleAccountSignedDelta(
            ChartOfAccounts account,
            Company company,
            BigDecimal signedDelta,
            String transactionType,
            String description,
            Long relatedId,
            Long relatedSubId,
            String sourceHint,
            String codePrefix) {
        if (signedDelta == null || signedDelta.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        Long companyId = auth.getCurrentCompanyId();
        if (!company.getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company mismatch");
        }
        if (!account.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to your company");
        }

        BigDecimal abs = signedDelta.abs();
        ChartOfAccounts credit = signedDelta.compareTo(BigDecimal.ZERO) > 0 ? account : null;
        ChartOfAccounts debit = signedDelta.compareTo(BigDecimal.ZERO) < 0 ? account : null;

        String src = normalizeSource(sourceHint);
        boolean locked = !SOURCE_UNKNOWN.equalsIgnoreCase(src);

        Transaction tx = Transaction.builder()
                .transactionCode(codePrefix + "-" + (relatedId != null ? relatedId : "0") + "-" + System.currentTimeMillis())
                .transactionType(transactionType)
                .company(company)
                .amount(abs)
                .transactionDate(LocalDate.now())
                .creditAccount(credit)
                .debitAccount(debit)
                .source(src)
                .sourceLocked(locked)
                .transactionDescription(description)
                .relatedId(relatedId)
                .relatedSubId(relatedSubId)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(repo.findById(saved.getId()).orElseThrow());
    }

    /** Avoids double GL impact when a budget line is approved more than once (e.g. hold then re-approve). */
    public boolean hasBudgetPostingForLine(Long budgetLineId) {
        if (budgetLineId == null) {
            return false;
        }
        return repo.existsByRelatedSubIdAndTransactionType(budgetLineId, TYPE_BUDGET);
    }

    @Transactional
    public TransactionResponseDTO recordReconciliationConfirmation(Reconciliation rec) {
        ChartOfAccounts account = coaRepo.findById(rec.getAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Company company = rec.getCompany();
        String desc = rec.getReason() != null && !rec.getReason().isBlank()
                ? rec.getReason()
                : "Reconciliation";
        return recordSingleAccountSignedDelta(
                account,
                company,
                rec.getAmount(),
                TYPE_RECONCILIATION,
                desc,
                rec.getId(),
                null,
                rec.getResource(),
                "TX-REC");
    }

    @Transactional
    public TransactionResponseDTO recordBudgetLineApproved(BudgetLine line) {
        ChartOfAccounts account = coaRepo.findById(line.getAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Company company = line.getBudgetHeader().getCompany();
        String desc = "Budget: " + line.getBudgetHeader().getBudgetName();
        if (line.getNotes() != null && !line.getNotes().isBlank()) {
            desc = desc + " — " + line.getNotes();
        }
        return recordSingleAccountSignedDelta(
                account,
                company,
                line.getAmount(),
                TYPE_BUDGET,
                desc,
                line.getBudgetHeader().getId(),
                line.getId(),
                null,
                "TX-BUD");
    }

    @Transactional
    public TransactionResponseDTO create(CreateTransactionDTO dto) {
        if (dto.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }

        Long debitId = dto.getDebitAccount();
        Long creditId = dto.getCreditAccount();
        validateDebitCreditLegs(debitId, creditId);

        Company company = companyRepo.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts creditAccount = creditId != null
                ? coaRepo.findById(creditId).orElseThrow(() -> new RuntimeException("Invalid credit account"))
                : null;
        ChartOfAccounts debitAccount = debitId != null
                ? coaRepo.findById(debitId).orElseThrow(() -> new RuntimeException("Invalid debit account"))
                : null;

        boolean singleSided = isSingleSided(debitAccount, creditAccount);

        Transaction.TransactionBuilder tb = Transaction.builder()
                .transactionCode("TX-" + System.currentTimeMillis())
                .transactionType(dto.getTransactionType() != null ? dto.getTransactionType() : TYPE_MANUAL)
                .company(company)
                .amount(dto.getAmount())
                .transactionDate(dto.getTransactionDate() != null ? dto.getTransactionDate() : LocalDate.now())
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
                .invoiceId(dto.getInvoiceId())
                .paymentId(dto.getPaymentId())
                .transactionDescription(dto.getTransactionDescription())
                .createdBy(auth.getCurrentUserId())
                .relatedId(dto.getRelatedId())
                .relatedSubId(dto.getRelatedSubId())
                .createdAt(Instant.now());

        if (singleSided) {
            String source = normalizeSource(dto.getSource());
            tb.source(source).sourceLocked(!SOURCE_UNKNOWN.equalsIgnoreCase(source));
        } else {
            tb.source(null).sourceLocked(false);
        }

        Transaction tx = tb.build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(repo.findById(saved.getId()).orElseThrow());
    }

    private static void validateDebitCreditLegs(Long debitId, Long creditId) {
        boolean hasDebit = debitId != null && debitId > 0;
        boolean hasCredit = creditId != null && creditId > 0;
        if (!hasDebit && !hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either debit or credit account (or both) is required");
        }
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return SOURCE_UNKNOWN;
        }
        return source.trim();
    }

    /** Exactly one of debit or credit (single-sided); both sides present = two-sided. */
    private static boolean isSingleSided(ChartOfAccounts debit, ChartOfAccounts credit) {
        return (debit == null) != (credit == null);
    }

    private static boolean isSingleSided(Transaction tx) {
        return (tx.getDebitAccount() == null) != (tx.getCreditAccount() == null);
    }

    /**
     * Applies COA balance changes for one- or two-sided transactions. Matches legacy
     * {@code updateGlAndCoaForPostedTransaction} behaviour for two-sided rows.
     */
    void applyPostingToCoa(Transaction tx) {
        BigDecimal amt = tx.getAmount();
        if (tx.getDebitAccount() != null) {
            ChartOfAccounts debitCoa = coaRepo.findById(tx.getDebitAccount().getId())
                    .orElseThrow(() -> new RuntimeException("Debit COA not found"));
            coaAdjust(debitCoa, amt.negate());
        }
        if (tx.getCreditAccount() != null) {
            ChartOfAccounts creditCoa = coaRepo.findById(tx.getCreditAccount().getId())
                    .orElseThrow(() -> new RuntimeException("Credit COA not found"));
            coaAdjust(creditCoa, amt);
        }
    }

    private void coaAdjust(ChartOfAccounts coa, BigDecimal delta) {
        CoaBalanceRules.assertSufficientBalance(coa, delta);
        BigDecimal current = coa.getBalance() == null ? BigDecimal.ZERO : coa.getBalance();
        coa.setBalance(current.add(delta));
        coaRepo.save(coa);
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

    @Transactional
    public TransactionResponseDTO postTransaction(Long id, String fiscalYear) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (tx.getCreditAccount() != null) {
            updateBalance(tx.getCreditAccount().getId(), fiscalYear, tx.getAmount(), false);
        }
        return toDTO(repo.save(tx));
    }

    private void updateBalance(Long accountId, String year, BigDecimal amount, boolean isDebit) {
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

    @Transactional
    public TransactionResponseDTO updateSource(Long id, UpdateTransactionSourceDTO dto) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        Long companyId = auth.getCurrentCompanyId();
        if (tx.getCompany() == null || !tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction not in your company");
        }
        if (!isSingleSided(tx)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source applies only to single-sided transactions (exactly one of debit or credit account)");
        }
        if (Boolean.TRUE.equals(tx.getSourceLocked())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source cannot be changed");
        }
        String current = tx.getSource() == null ? SOURCE_UNKNOWN : tx.getSource();
        if (!SOURCE_UNKNOWN.equalsIgnoreCase(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source can only be edited when UNKNOWN");
        }
        String newSource = dto.getSource() == null || dto.getSource().isBlank()
                ? SOURCE_UNKNOWN
                : dto.getSource().trim();
        if (SOURCE_UNKNOWN.equalsIgnoreCase(newSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set a concrete source value");
        }
        tx.setSource(newSource);
        tx.setSourceLocked(true);
        return toDTO(repo.save(tx));
    }

    @Transactional
    public TransactionResponseDTO update(Long id, UpdateTransactionDTO dto) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        Long companyId = auth.getCurrentCompanyId();
        if (tx.getCompany() == null || !tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction not in your company");
        }
        if (dto.getTransactionType() != null) {
            tx.setTransactionType(dto.getTransactionType());
        }
        if (dto.getTransactionDate() != null) {
            tx.setTransactionDate(dto.getTransactionDate());
        }
        if (dto.getAmount() != null) {
            tx.setAmount(dto.getAmount());
        }
        if (dto.getTransactionDescription() != null) {
            tx.setTransactionDescription(dto.getTransactionDescription());
        }
        return toDTO(repo.save(tx));
    }

    private TransactionResponseDTO toDTO(Transaction tx) {
        TransactionResponseDTO.TransactionResponseDTOBuilder b = TransactionResponseDTO.builder()
                .id(tx.getId())
                .transactionCode(tx.getTransactionCode())
                .transactionType(tx.getTransactionType())
                .transactionDate(tx.getTransactionDate())
                .amount(tx.getAmount())
                .invoiceId(tx.getInvoiceId())
                .paymentId(tx.getPaymentId())
                .transactionDescription(tx.getTransactionDescription())
                .relatedId(tx.getRelatedId())
                .relatedSubId(tx.getRelatedSubId());

        if (isSingleSided(tx)) {
            b.source(tx.getSource() != null ? tx.getSource() : SOURCE_UNKNOWN)
                    .sourceLocked(Boolean.TRUE.equals(tx.getSourceLocked()));
        } else {
            b.source(null).sourceLocked(false);
        }

        if (tx.getCreditAccount() != null) {
            b.creditAccountId(tx.getCreditAccount().getId())
                    .creditAccountName(tx.getCreditAccount().getAccountName());
        }
        if (tx.getDebitAccount() != null) {
            b.debitAccountId(tx.getDebitAccount().getId())
                    .debitAccountName(tx.getDebitAccount().getAccountName());
        }
        if (tx.getCompany() != null) {
            b.companyId(tx.getCompany().getId())
                    .companyName(tx.getCompany().getCompanyName());
        }
        return b.build();
    }

    @Transactional
    public Transaction createTransactionForPayment(Long paymentId,
                                                   Long companyId,
                                                   BigDecimal amount,
                                                   Long debitAccountId,
                                                   Long creditAccountId,
                                                   LocalDate txDate,
                                                   String txType) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts creditAccount = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid credit account"));

        ChartOfAccounts debitAccount = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid debit account"));

        Transaction tx = Transaction.builder()
                .transactionCode("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionType(txType != null ? txType : TYPE_PAYMENT)
                .company(company)
                .transactionDate(txDate == null ? LocalDate.now() : txDate)
                .amount(amount)
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
                .paymentId(paymentId != null ? String.valueOf(paymentId) : null)
                .source(null)
                .sourceLocked(false)
                .createdAt(Instant.now())
                .createdBy(null)
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return saved;
    }

    @Transactional
    public TransactionResponseDTO createSalesOrderCancelReversal(
            Long companyId,
            Long relatedSalesOrderId,
            BigDecimal amount,
            Long originalDebitAccountId,
            Long originalCreditAccountId
    ) {
        if (relatedSalesOrderId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid sales order id and amount are required");
        }
        if (originalDebitAccountId == null || originalCreditAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sales order accounts are required");
        }
        if (repo.existsByRelatedIdAndTransactionType(relatedSalesOrderId, TYPE_SALES_ORDER_CANCEL_REVERSAL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sales order cancellation reversal already exists");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Reverse the original accounting direction by swapping debit/credit legs.
        ChartOfAccounts debitAccount = coaRepo.findById(originalCreditAccountId)
                .orElseThrow(() -> new RuntimeException("Reverse debit account not found"));
        ChartOfAccounts creditAccount = coaRepo.findById(originalDebitAccountId)
                .orElseThrow(() -> new RuntimeException("Reverse credit account not found"));

        Transaction tx = Transaction.builder()
                .transactionCode("TX-SO-CAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionType(TYPE_SALES_ORDER_CANCEL_REVERSAL)
                .company(company)
                .transactionDate(LocalDate.now())
                .amount(amount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .transactionDescription("Sales order cancellation reversal")
                .relatedId(relatedSalesOrderId)
                .source(null)
                .sourceLocked(false)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(saved);
    }

    /**
     * Records vendor payment settlement: decreases accounts payable and cash.
     * Does not use {@link #applyPostingToCoa} (that moves one account down and one up); both legs must decrease.
     */
    @Transactional
    public TransactionResponseDTO createVendorPaymentSettlement(
            Long paymentId,
            Long companyId,
            BigDecimal amount,
            Long accountsPayableAccountId,
            Long cashAccountId,
            LocalDate txDate,
            Long relatedPurchaseOrderId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        ChartOfAccounts ap = coaRepo.findById(accountsPayableAccountId)
                .orElseThrow(() -> new RuntimeException("Accounts payable account not found"));
        ChartOfAccounts cash = coaRepo.findById(cashAccountId)
                .orElseThrow(() -> new RuntimeException("Cash account not found"));

        Transaction tx = Transaction.builder()
                .transactionCode("TX-VP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transactionType(TYPE_VENDOR_PAYMENT)
                .company(company)
                .transactionDate(txDate == null ? LocalDate.now() : txDate)
                .amount(amount)
                .debitAccount(ap)
                .creditAccount(cash)
                .paymentId(paymentId != null ? String.valueOf(paymentId) : null)
                .transactionDescription("Vendor payment — AP and cash")
                .relatedId(relatedPurchaseOrderId)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        coaAdjust(ap, amount.negate());
        coaAdjust(cash, amount.negate());
        return toDTO(saved);
    }
}
