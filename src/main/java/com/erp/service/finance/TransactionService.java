package com.erp.service.finance;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.BudgetLine;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.GLAccountBalance;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.Payment;
import com.erp.domain.finance.Reconciliation;
import com.erp.domain.finance.Transaction;
import com.erp.domain.hr.Company;
import com.erp.dto.finance.BudgetDistributionResponseDTO;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.finance.UpdateTransactionDTO;
import com.erp.dto.finance.UpdateTransactionSourceDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.GLAccountBalanceRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.erp.service.DocumentSequenceService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    public static final String SOURCE_UNKNOWN = "UNKNOWN";
    public static final String SOURCE_SALE = "sale";
    public static final String SOURCE_PURCHASE = "purchase";
    public static final String SOURCE_OTHER = "other";
    public static final String TYPE_OPENING_BALANCE = "OPENING_BALANCE";
    public static final String TYPE_PAYMENT = "PAYMENT";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_JOURNAL = "JOURNAL";
    public static final String TYPE_RECONCILIATION = "RECONCILIATION";
    public static final String TYPE_BUDGET = "BUDGET";
    public static final String TYPE_BUDGET_DISTRIBUTION = "BUDGET_DISTRIBUTION";
    /** Legacy / historical rows: two-sided posting when a PR was approved (accrual); new flows post at vendor payment. */
    public static final String TYPE_PURCHASE_REQUISITION = "PURCHASE_REQUISITION";
    /** Vendor payment confirmed in AP: expense/inventory debit and cash credit via {@link #applyPostingToCoa}. */
    public static final String TYPE_VENDOR_PAYMENT = "VENDOR_PAYMENT";
    /** PO released: commit funds from purchase debit/credit defaults (debit -=, credit +=). */
    public static final String TYPE_PURCHASE_ORDER_ENCUMBRANCE = "PURCHASE_ORDER_ENCUMBRANCE";
    public static final String TYPE_PURCHASE_ORDER_CANCEL_REVERSAL = "PURCHASE_ORDER_CANCEL_REVERSAL";
    public static final String TYPE_SALES_ORDER_CANCEL_REVERSAL = "SALES_ORDER_CANCEL_REVERSAL";
    public static final String TYPE_STOCK_VARIANCE = "STOCK_VARIANCE";
    public static final String TYPE_PAYROLL = "PAYROLL";
    /** Ad-hoc expense payment confirmed in AP (rent, reimbursements, etc.) — not tied to a PO/invoice. */
    public static final String TYPE_OTHER_PAYMENT = "OTHER_PAYMENT";

    private final TransactionRepository repo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;
    private final ChartOfAccountsRepository coaRepo;
    private final GLAccountBalanceRepository glRepo;
    private final DocumentSequenceService documentSequenceService;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;

    public TransactionService(
            TransactionRepository repo,
            CompanyRepository companyRepo,
            AuthContext auth,
            ChartOfAccountsRepository coaRepo,
            GLAccountBalanceRepository glRepo,
            DocumentSequenceService documentSequenceService,
            UserRepository userRepository,
            InvoiceRepository invoiceRepo,
            PaymentRepository paymentRepo
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.coaRepo = coaRepo;
        this.glRepo = glRepo;
        this.documentSequenceService = documentSequenceService;
        this.userRepository = userRepository;
        this.invoiceRepo = invoiceRepo;
        this.paymentRepo = paymentRepo;
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
                .transactionCode(documentSequenceService.generateNext("TX"))
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
                .transactionCode(documentSequenceService.generateNext(codePrefix))
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

    /** Credit the linked budget COA when a budget header is first approved. */
    @Transactional
    public void recordBudgetApproved(com.erp.domain.finance.BudgetHeader header) {
        if (header.getBudgetAccount() == null || header.getAmount() == null) {
            return;
        }
        if (repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                header.getCompany().getId(), header.getId(), TYPE_BUDGET)) {
            return;
        }
        ChartOfAccounts budgetAccount = coaRepo.findById(header.getBudgetAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget account not found"));
        String desc = "Budget approved: " + header.getBudgetName();
        recordSingleAccountSignedDelta(
                budgetAccount,
                header.getCompany(),
                header.getAmount(),
                TYPE_BUDGET,
                desc,
                header.getId(),
                null,
                null,
                "TX-BUD");
    }

    /** Adjust budget COA when a revised budget amount changes. */
    @Transactional
    public void recordBudgetRevisionAdjustment(
            com.erp.domain.finance.BudgetHeader header,
            BigDecimal oldAmount,
            BigDecimal newAmount) {
        if (header.getBudgetAccount() == null || oldAmount == null || newAmount == null) {
            return;
        }
        BigDecimal delta = newAmount.subtract(oldAmount);
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        ChartOfAccounts budgetAccount = coaRepo.findById(header.getBudgetAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget account not found"));
        String desc = "Budget revision: " + header.getBudgetName();
        recordSingleAccountSignedDelta(
                budgetAccount,
                header.getCompany(),
                delta,
                TYPE_BUDGET,
                desc,
                header.getId(),
                null,
                null,
                "TX-BUD");
    }

    /** Double-entry GL posting when payroll is generated: debit salary expense, credit payroll payable/bank. */
    @Transactional
    public void recordPayrollPosting(
            Long companyId,
            Long payrollId,
            BigDecimal grossPay,
            Long debitAccountId,
            Long creditAccountId,
            String description) {
        if (grossPay == null || grossPay.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (repo.existsByCompanyIdAndRelatedIdAndTransactionType(companyId, payrollId, TYPE_PAYROLL)) {
            return;
        }
        ChartOfAccounts debitAccount = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll debit account not found"));
        ChartOfAccounts creditAccount = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll credit account not found"));
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX-PAY"))
                .transactionType(TYPE_PAYROLL)
                .company(company)
                .amount(grossPay)
                .transactionDate(LocalDate.now())
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .source("PAYROLL")
                .sourceLocked(true)
                .transactionDescription(description)
                .relatedId(payrollId)
                .createdAt(Instant.now())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
    }

    /** Two-sided GL posting for budget distribution: debit budget account, credit target account. */
    @Transactional
    public Transaction recordBudgetDistribution(
            com.erp.domain.finance.BudgetHeader header,
            ChartOfAccounts creditAccount,
            BigDecimal amount,
            String notes,
            LocalDate postedDate) {
        if (header.getBudgetAccount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget has no linked budget account");
        }
        ChartOfAccounts budgetAccount = coaRepo.findById(header.getBudgetAccount().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget account not found"));

        Long companyId = auth.getCurrentCompanyId();
        if (!header.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company mismatch");
        }

        String desc = "Budget distribution: " + header.getBudgetName();
        if (notes != null && !notes.isBlank()) {
            desc = desc + " — " + notes;
        }

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX-BUD"))
                .transactionType(TYPE_BUDGET_DISTRIBUTION)
                .company(header.getCompany())
                .amount(amount)
                .transactionDate(postedDate != null ? postedDate : LocalDate.now())
                .debitAccount(budgetAccount)
                .creditAccount(creditAccount)
                .transactionDescription(desc)
                .relatedId(header.getId())
                .relatedSubId(creditAccount.getId())
                .source(null)
                .sourceLocked(false)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .archived(false)
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return saved;
    }

    public List<BudgetDistributionResponseDTO> listBudgetDistributions(
            Long budgetId,
            LocalDate from,
            LocalDate to,
            Boolean archived) {
        return repo.findBudgetTransactions(budgetId, TYPE_BUDGET_DISTRIBUTION, from, to, archived).stream()
                .map(this::toBudgetDistributionDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public int archiveBudgetDistributions(Long budgetId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range is required");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must be before to date");
        }
        com.erp.domain.User user = com.erp.domain.User.builder()
                .id(auth.getCurrentUserId())
                .build();
        List<Transaction> rows = repo.findBudgetTransactions(
                budgetId, TYPE_BUDGET_DISTRIBUTION, from, to, false);
        Instant now = Instant.now();
        for (Transaction tx : rows) {
            tx.setArchived(true);
            tx.setArchivedAt(now);
            tx.setArchivedByUser(user);
        }
        repo.saveAll(rows);
        return rows.size();
    }

    private BudgetDistributionResponseDTO toBudgetDistributionDTO(Transaction tx) {
        BudgetDistributionResponseDTO.BudgetDistributionResponseDTOBuilder b =
                BudgetDistributionResponseDTO.builder()
                        .id(tx.getId())
                        .transactionCode(tx.getTransactionCode())
                        .transactionDate(tx.getTransactionDate())
                        .amount(tx.getAmount())
                        .transactionDescription(tx.getTransactionDescription())
                        .createdAt(tx.getCreatedAt())
                        .archived(Boolean.TRUE.equals(tx.getArchived()));

        if (tx.getCreatedBy() != null) {
            b.createdByUserId(tx.getCreatedBy());
            userRepository.findById(tx.getCreatedBy())
                    .ifPresent(u -> b.createdByUserName(u.getFullName()));
        }

        if (tx.getCreditAccount() != null) {
            b.creditAccountId(tx.getCreditAccount().getId())
                    .creditAccountName(tx.getCreditAccount().getAccountName())
                    .creditAccountCode(tx.getCreditAccount().getAccountCode());
        }
        if (tx.getDebitAccount() != null) {
            b.debitAccountId(tx.getDebitAccount().getId())
                    .debitAccountName(tx.getDebitAccount().getAccountName());
        }
        if (tx.getArchivedAt() != null) {
            b.archivedAt(tx.getArchivedAt());
        }
        if (tx.getArchivedByUser() != null) {
            b.archivedByUserId(tx.getArchivedByUser().getId())
                    .archivedByUserName(tx.getArchivedByUser().getFullName());
        }
        return b.build();
    }

    @Transactional
    public TransactionResponseDTO create(CreateTransactionDTO dto) {
        if (dto.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }

        Long debitId = dto.getDebitAccount();
        Long creditId = dto.getCreditAccount();
        validateDebitCreditLegs(debitId, creditId);

        Long requestedCompanyId = dto.getCompanyId();
        if (!isSuperAdmin()) {
            Long cid = auth.getCurrentCompanyId();
            if (cid == null || requestedCompanyId == null || !cid.equals(requestedCompanyId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company mismatch");
            }
        }

        Company company = companyRepo.findById(requestedCompanyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts creditAccount = creditId != null
                ? coaRepo.findById(creditId).orElseThrow(() -> new RuntimeException("Invalid credit account"))
                : null;
        ChartOfAccounts debitAccount = debitId != null
                ? coaRepo.findById(debitId).orElseThrow(() -> new RuntimeException("Invalid debit account"))
                : null;

        if (debitAccount != null && !debitAccount.getCompany().getId().equals(requestedCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debit account does not belong to this company");
        }
        if (creditAccount != null && !creditAccount.getCompany().getId().equals(requestedCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credit account does not belong to this company");
        }

        boolean singleSided = isSingleSided(debitAccount, creditAccount);

        String invoiceId = resolveInvoiceIdForCreate(
                dto.getInvoiceId(), dto.getPaymentId(), dto.getRelatedId(), dto.getTransactionType());
        String procSource = defaultSalePurchaseSource(dto.getTransactionType());

        Transaction.TransactionBuilder tb = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX"))
                .transactionType(dto.getTransactionType() != null ? dto.getTransactionType() : TYPE_MANUAL)
                .company(company)
                .amount(dto.getAmount())
                .transactionDate(dto.getTransactionDate() != null ? dto.getTransactionDate() : LocalDate.now())
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
                .invoiceId(invoiceId)
                .paymentId(dto.getPaymentId())
                .transactionDescription(dto.getTransactionDescription())
                .createdBy(auth.getCurrentUserId())
                .relatedId(dto.getRelatedId())
                .relatedSubId(dto.getRelatedSubId())
                .createdAt(Instant.now());

        if (singleSided) {
            String source = procSource != null ? procSource : normalizeSource(dto.getSource());
            tb.source(source).sourceLocked(procSource != null || !SOURCE_UNKNOWN.equalsIgnoreCase(source));
        } else if (procSource != null) {
            tb.source(procSource).sourceLocked(true);
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

    /**
     * Validates that posting {@code amount} with the same deltas as {@link #applyPostingToCoa} would not violate
     * {@link CoaBalanceRules}.
     */
    public void validateTwoSidedPostingBalances(
            Long debitAccountId,
            Long creditAccountId,
            BigDecimal amount,
            Long companyId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive for posting validation");
        }
        if (debitAccountId == null || creditAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit accounts are required");
        }
        if (debitAccountId.equals(creditAccountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit accounts cannot be the same");
        }
        ChartOfAccounts debit = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Debit account not found"));
        ChartOfAccounts credit = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Credit account not found"));
        if (!debit.getCompany().getId().equals(companyId)
                || !credit.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to this company");
        }
        CoaBalanceRules.assertSufficientBalance(debit, amount.negate());
        CoaBalanceRules.assertSufficientBalance(credit, amount);
    }

    private void coaAdjust(ChartOfAccounts coa, BigDecimal delta) {
        CoaBalanceRules.assertSufficientBalance(coa, delta);
        BigDecimal current = coa.getBalance() == null ? BigDecimal.ZERO : coa.getBalance();
        coa.setBalance(current.add(delta));
        coaRepo.save(coa);
    }

    private boolean isSuperAdmin() {
        String r = auth.getCurrentUserRole();
        return r != null && "SUPER_ADMIN".equalsIgnoreCase(r);
    }

    private void assertTransactionCompany(Transaction tx) {
        if (isSuperAdmin()) {
            return;
        }
        Long companyId = auth.getCurrentCompanyId();
        if (tx.getCompany() == null || companyId == null || !tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction not in your company");
        }
    }

    public boolean hasPurchaseOrderVendorPaymentPosting(Long companyId, Long purchaseOrderId) {
        return companyId != null
                && purchaseOrderId != null
                && repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                        companyId, purchaseOrderId, TYPE_VENDOR_PAYMENT);
    }

    public boolean hasPurchaseOrderEncumbrance(Long companyId, Long purchaseOrderId) {
        return companyId != null
                && purchaseOrderId != null
                && repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                        companyId, purchaseOrderId, TYPE_PURCHASE_ORDER_ENCUMBRANCE);
    }

    public TransactionResponseDTO get(Long id) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        assertTransactionCompany(tx);
        return toDTO(tx);
    }

    public List<TransactionResponseDTO> listByCompany(Long companyId) {
        Long effectiveCompanyId = isSuperAdmin() ? companyId : auth.getCurrentCompanyId();
        if (effectiveCompanyId == null) {
            return List.of();
        }
        return repo.findByCompanyIdOrderByCreatedAtDesc(effectiveCompanyId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponseDTO archiveTransaction(Long id) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        assertTransactionCompany(tx);
        if (Boolean.TRUE.equals(tx.getArchived())) {
            return toDTO(tx);
        }

        com.erp.domain.User user = userRepository.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        tx.setArchived(true);
        tx.setArchivedAt(Instant.now());
        tx.setArchivedByUser(user);
        return toDTO(repo.save(tx));
    }

    @Transactional
    public TransactionResponseDTO postTransaction(Long id, String fiscalYear) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        assertTransactionCompany(tx);

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
        String procSource = defaultSalePurchaseSource(tx.getTransactionType());
        TransactionResponseDTO.TransactionResponseDTOBuilder b = TransactionResponseDTO.builder()
                .id(tx.getId())
                .transactionCode(tx.getTransactionCode())
                .transactionType(tx.getTransactionType())
                .transactionDate(tx.getTransactionDate())
                .amount(tx.getAmount())
                .invoiceId(resolveDisplayInvoiceId(tx))
                .paymentId(tx.getPaymentId())
                .transactionDescription(tx.getTransactionDescription())
                .relatedId(tx.getRelatedId())
                .relatedSubId(tx.getRelatedSubId());

        if (procSource != null) {
            b.source(procSource).sourceLocked(true);
        } else if (isSingleSided(tx)) {
            b.source(tx.getSource() != null ? tx.getSource() : SOURCE_UNKNOWN)
                    .sourceLocked(Boolean.TRUE.equals(tx.getSourceLocked()));
        } else {
            b.source(null).sourceLocked(false);
        }

        if (tx.getCreditAccount() != null) {
            b.creditAccountId(tx.getCreditAccount().getId())
                    .creditAccountCode(tx.getCreditAccount().getAccountCode())
                    .creditAccountName(tx.getCreditAccount().getAccountName());
        }
        if (tx.getDebitAccount() != null) {
            b.debitAccountId(tx.getDebitAccount().getId())
                    .debitAccountCode(tx.getDebitAccount().getAccountCode())
                    .debitAccountName(tx.getDebitAccount().getAccountName());
        }
        if (tx.getCompany() != null) {
            b.companyId(tx.getCompany().getId())
                    .companyName(tx.getCompany().getCompanyName());
        }
        b.archived(Boolean.TRUE.equals(tx.getArchived()))
                .archivedAt(tx.getArchivedAt())
                .createdAt(tx.getCreatedAt())
                .createdByUserId(tx.getCreatedBy());
        if (tx.getArchivedByUser() != null) {
            b.archivedByUserId(tx.getArchivedByUser().getId())
                    .archivedByUserName(tx.getArchivedByUser().getFullName());
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
                                                   String txType,
                                                   String invoiceId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts creditAccount = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid credit account"));

        ChartOfAccounts debitAccount = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Invalid debit account"));

        String resolvedInvoiceId = resolveInvoiceIdForCreate(
                invoiceId, paymentId != null ? String.valueOf(paymentId) : null, null, txType);
        String procSource = defaultSalePurchaseSource(txType != null ? txType : TYPE_PAYMENT);

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX"))
                .transactionType(txType != null ? txType : TYPE_PAYMENT)
                .company(company)
                .transactionDate(txDate == null ? LocalDate.now() : txDate)
                .amount(amount)
                .creditAccount(creditAccount)
                .debitAccount(debitAccount)
                .paymentId(paymentId != null ? String.valueOf(paymentId) : null)
                .invoiceId(resolvedInvoiceId)
                .source(procSource)
                .sourceLocked(procSource != null)
                .createdAt(Instant.now())
                .createdBy(null)
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return saved;
    }

    /**
     * Backfills {@code invoice_id} on PO-related GL rows (encumbrance, vendor payment, cancel reversal)
     * after the generated purchase invoice exists.
     */
    @Transactional
    public void linkInvoiceToPurchaseOrderTransactions(Long purchaseOrderId, String invoiceId) {
        if (purchaseOrderId == null || invoiceId == null || invoiceId.isBlank()) {
            return;
        }
        List<String> linkableTypes = List.of(
                TYPE_PURCHASE_ORDER_ENCUMBRANCE,
                TYPE_PURCHASE_ORDER_CANCEL_REVERSAL,
                TYPE_VENDOR_PAYMENT,
                TYPE_PURCHASE_REQUISITION);
        List<Transaction> toUpdate = repo.findByRelatedIdAndInvoiceIdIsNull(purchaseOrderId).stream()
                .filter(tx -> tx.getTransactionType() != null
                        && linkableTypes.contains(tx.getTransactionType()))
                .toList();
        for (Transaction tx : toUpdate) {
            tx.setInvoiceId(invoiceId);
            applyPurchaseSource(tx);
        }
        if (!toUpdate.isEmpty()) {
            repo.saveAll(toUpdate);
        }
    }

    @Transactional
    public TransactionResponseDTO createSalesOrderCancelReversal(
            Long companyId,
            Long relatedSalesOrderId,
            BigDecimal amount,
            Long originalDebitAccountId,
            Long originalCreditAccountId,
            String invoiceId
    ) {
        if (relatedSalesOrderId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid sales order id and amount are required");
        }
        if (originalDebitAccountId == null || originalCreditAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sales order accounts are required");
        }
        if (repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                companyId, relatedSalesOrderId, TYPE_SALES_ORDER_CANCEL_REVERSAL)) {
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
                .transactionCode(documentSequenceService.generateNext("TX-SO-CAN"))
                .transactionType(TYPE_SALES_ORDER_CANCEL_REVERSAL)
                .company(company)
                .transactionDate(LocalDate.now())
                .amount(amount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .transactionDescription("Sales order cancellation reversal")
                .relatedId(relatedSalesOrderId)
                .invoiceId(invoiceId != null && !invoiceId.isBlank()
                        ? invoiceId
                        : resolveSalesInvoiceCode(relatedSalesOrderId))
                .source(SOURCE_SALE)
                .sourceLocked(true)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO createPurchaseOrderEncumbrance(
            Long companyId,
            Long purchaseOrderId,
            BigDecimal amount,
            Long debitAccountId,
            Long creditAccountId,
            String orderNumber
    ) {
        if (purchaseOrderId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid purchase order id and amount are required");
        }
        if (debitAccountId == null || creditAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order posting accounts are required");
        }
        if (repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                companyId, purchaseOrderId, TYPE_PURCHASE_ORDER_ENCUMBRANCE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order encumbrance already posted");
        }

        validateTwoSidedPostingBalances(debitAccountId, creditAccountId, amount, companyId);

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts debitAccount = coaRepo.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Debit account not found"));
        ChartOfAccounts creditAccount = coaRepo.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        String desc = "Purchase order encumbrance"
                + (orderNumber != null && !orderNumber.isBlank() ? " — PO " + orderNumber : "");

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX-PO"))
                .transactionType(TYPE_PURCHASE_ORDER_ENCUMBRANCE)
                .company(company)
                .transactionDate(LocalDate.now())
                .amount(amount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .transactionDescription(desc)
                .relatedId(purchaseOrderId)
                .invoiceId(resolvePurchaseInvoiceCode(purchaseOrderId))
                .source(SOURCE_PURCHASE)
                .sourceLocked(true)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(saved);
    }

    @Transactional
    public TransactionResponseDTO createPurchaseOrderCancelReversal(
            Long companyId,
            Long purchaseOrderId,
            BigDecimal amount,
            Long originalDebitAccountId,
            Long originalCreditAccountId,
            String orderNumber
    ) {
        if (purchaseOrderId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid purchase order id and amount are required");
        }
        if (originalDebitAccountId == null || originalCreditAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order accounts are required");
        }
        if (repo.existsByCompanyIdAndRelatedIdAndTransactionType(
                companyId, purchaseOrderId, TYPE_PURCHASE_ORDER_CANCEL_REVERSAL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order cancellation reversal already exists");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts debitAccount = coaRepo.findById(originalCreditAccountId)
                .orElseThrow(() -> new RuntimeException("Reverse debit account not found"));
        ChartOfAccounts creditAccount = coaRepo.findById(originalDebitAccountId)
                .orElseThrow(() -> new RuntimeException("Reverse credit account not found"));

        String desc = "Purchase order cancellation — release encumbrance"
                + (orderNumber != null && !orderNumber.isBlank() ? " — PO " + orderNumber : "");

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX-PO-CAN"))
                .transactionType(TYPE_PURCHASE_ORDER_CANCEL_REVERSAL)
                .company(company)
                .transactionDate(LocalDate.now())
                .amount(amount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .transactionDescription(desc)
                .relatedId(purchaseOrderId)
                .invoiceId(resolvePurchaseInvoiceCode(purchaseOrderId))
                .source(SOURCE_PURCHASE)
                .sourceLocked(true)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return toDTO(saved);
    }

    /**
     * Posts inventory variance to GL: loss reduces inventory asset; gain increases it.
     */
    @Transactional
    public Transaction createForStockVariance(
            Long companyId,
            Long varianceId,
            BigDecimal amount,
            Long inventoryAssetAccountId,
            Long varianceExpenseAccountId,
            boolean inventoryIncrease,
            LocalDate txDate,
            String description
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variance amount must be positive");
        }
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        ChartOfAccounts inventoryAsset = coaRepo.findById(inventoryAssetAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory asset account not found"));
        ChartOfAccounts varianceExpense = coaRepo.findById(varianceExpenseAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variance expense account not found"));

        if (!inventoryAsset.getCompany().getId().equals(companyId)
                || !varianceExpense.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accounts must belong to your company");
        }

        ChartOfAccounts debitAccount = inventoryIncrease ? inventoryAsset : varianceExpense;
        ChartOfAccounts creditAccount = inventoryIncrease ? varianceExpense : inventoryAsset;

        Transaction tx = Transaction.builder()
                .transactionCode(documentSequenceService.generateNext("TX-VAR"))
                .transactionType(TYPE_STOCK_VARIANCE)
                .company(company)
                .transactionDate(txDate != null ? txDate : LocalDate.now())
                .amount(amount)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .transactionDescription(description != null ? description : "Stock variance")
                .relatedId(varianceId)
                .source("STOCK_VARIANCE")
                .sourceLocked(true)
                .createdAt(Instant.now())
                .createdBy(auth.getCurrentUserId())
                .build();

        Transaction saved = repo.save(tx);
        applyPostingToCoa(saved);
        return saved;
    }

    private static String defaultSalePurchaseSource(String transactionType) {
        if (transactionType == null) {
            return null;
        }
        return switch (transactionType) {
            case TYPE_PAYMENT, TYPE_SALES_ORDER_CANCEL_REVERSAL, "PAYMENT_REVERSAL" -> SOURCE_SALE;
            case TYPE_VENDOR_PAYMENT, TYPE_PURCHASE_ORDER_ENCUMBRANCE,
                    TYPE_PURCHASE_ORDER_CANCEL_REVERSAL, TYPE_PURCHASE_REQUISITION -> SOURCE_PURCHASE;
            default -> null;
        };
    }

    private static boolean isPurchaseLinkedType(String transactionType) {
        return TYPE_VENDOR_PAYMENT.equals(transactionType)
                || TYPE_PURCHASE_ORDER_ENCUMBRANCE.equals(transactionType)
                || TYPE_PURCHASE_ORDER_CANCEL_REVERSAL.equals(transactionType)
                || TYPE_PURCHASE_REQUISITION.equals(transactionType);
    }

    private String resolvePurchaseInvoiceCode(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            return null;
        }
        return invoiceRepo.findByOrderIdAndType(purchaseOrderId, InvoiceType.PURCHASE)
                .map(Invoice::getInvoiceId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(null);
    }

    private String resolveSalesInvoiceCode(Long salesOrderId) {
        if (salesOrderId == null) {
            return null;
        }
        return invoiceRepo.findByOrderIdAndType(salesOrderId, InvoiceType.SALES)
                .map(Invoice::getInvoiceId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(null);
    }

    private String resolveInvoiceFromPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }
        try {
            Long id = Long.parseLong(paymentId.trim());
            return paymentRepo.findById(id)
                    .map(Payment::getInvoiceId)
                    .filter(invoice -> invoice != null && !invoice.isBlank())
                    .orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveInvoiceIdForCreate(
            String invoiceId, String paymentId, Long relatedId, String transactionType) {
        if (invoiceId != null && !invoiceId.isBlank()) {
            return invoiceId.trim();
        }
        String fromPayment = resolveInvoiceFromPayment(paymentId);
        if (fromPayment != null) {
            return fromPayment;
        }
        if (relatedId != null && isPurchaseLinkedType(transactionType)) {
            return resolvePurchaseInvoiceCode(relatedId);
        }
        if (relatedId != null && TYPE_SALES_ORDER_CANCEL_REVERSAL.equals(transactionType)) {
            return resolveSalesInvoiceCode(relatedId);
        }
        return null;
    }

    private String resolveDisplayInvoiceId(Transaction tx) {
        String resolved = resolveInvoiceIdForCreate(
                tx.getInvoiceId(),
                tx.getPaymentId(),
                tx.getRelatedId(),
                tx.getTransactionType());
        return resolved != null ? resolved : tx.getInvoiceId();
    }

    private void applyPurchaseSource(Transaction tx) {
        if (tx.getSource() == null || tx.getSource().isBlank()
                || SOURCE_UNKNOWN.equalsIgnoreCase(tx.getSource())) {
            tx.setSource(SOURCE_PURCHASE);
            tx.setSourceLocked(true);
        }
    }
}
