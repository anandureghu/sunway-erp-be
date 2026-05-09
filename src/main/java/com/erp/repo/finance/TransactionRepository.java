package com.erp.repo.finance;

import com.erp.domain.finance.COAType;
import com.erp.domain.finance.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Transaction> findByPaymentIdOrderByCreatedAtDesc(String paymentId);
    List<Transaction> findByPaymentIdAndTransactionTypeOrderByCreatedAtDesc(String paymentId, String transactionType);
    boolean existsByPaymentId(String paymentId);
    boolean existsByRelatedIdAndTransactionType(Long relatedId, String transactionType);

    boolean existsByRelatedSubIdAndTransactionType(Long relatedSubId, String transactionType);

    // ======================================================
    //  Finance report aggregations
    // ======================================================

    /**
     * Aggregate transactions by their CREDIT account, restricted to certain COA types.
     * Used to compute income totals (REVENUE / INCOME accounts are normally credit-side).
     * Returns rows of (accountName, accountCode, totalAmount).
     */
    @Query("""
            SELECT a.accountName, a.accountCode, COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            JOIN t.creditAccount a
            WHERE t.company.id = :companyId
              AND a.type IN :types
              AND (:from IS NULL OR t.transactionDate >= :from)
              AND (:to IS NULL OR t.transactionDate <= :to)
            GROUP BY a.accountName, a.accountCode
            ORDER BY COALESCE(SUM(t.amount), 0) DESC
            """)
    List<Object[]> aggregateByCreditAccountTypes(
            @Param("companyId") Long companyId,
            @Param("types") Collection<COAType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /**
     * Aggregate transactions by their DEBIT account, restricted to certain COA types.
     * Used to compute expense totals (EXPENSE / COST accounts are normally debit-side).
     * Returns rows of (accountName, accountCode, totalAmount).
     */
    @Query("""
            SELECT a.accountName, a.accountCode, COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            JOIN t.debitAccount a
            WHERE t.company.id = :companyId
              AND a.type IN :types
              AND (:from IS NULL OR t.transactionDate >= :from)
              AND (:to IS NULL OR t.transactionDate <= :to)
            GROUP BY a.accountName, a.accountCode
            ORDER BY COALESCE(SUM(t.amount), 0) DESC
            """)
    List<Object[]> aggregateByDebitAccountTypes(
            @Param("companyId") Long companyId,
            @Param("types") Collection<COAType> types,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
