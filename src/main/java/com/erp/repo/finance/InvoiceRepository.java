package com.erp.repo.finance;

import com.erp.domain.finance.Invoice;
import com.erp.domain.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceId(String invoiceId);

    List<Invoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Invoice> findByCompany_IdAndTypeOrderByCreatedAtDesc(Long companyId, InvoiceType type);

    Page<Invoice> findByCompany_IdAndArchivedTrueAndTypeOrderByCreatedAtDesc(
            Long companyId, InvoiceType type, Pageable pageable);

    List<Invoice> findByCompany_IdAndArchivedTrueAndType(Long companyId, InvoiceType type);

    Invoice findByOrderId(Long orderId);
    Optional<Invoice> findByOrderIdAndType(Long orderId, InvoiceType type);

    Optional<Invoice> findByCompany_IdAndOrderIdAndTypeAndSupplierInvoiceNumber(
            Long companyId,
            Long orderId,
            InvoiceType type,
            String supplierInvoiceNumber
    );

    List<Invoice> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);

    List<Invoice> findByToPartyOrderByCreatedAtDesc(String toParty); // customer name/id

    List<Invoice> findByCompany_IdAndToPartyOrderByCreatedAtDesc(Long companyId, String toParty);

    // ======================================================
    //  Finance report aggregations
    // ======================================================

    /** Sum of invoice amounts in [from, to] for a given type, scoped to company. */
    @Query("""
            SELECT COALESCE(SUM(i.amount), 0)
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND (:from IS NULL OR i.invoiceDate >= :from)
              AND (:to IS NULL OR i.invoiceDate <= :to)
            """)
    BigDecimal sumByTypeBetween(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Total outstanding (open) for a given type. */
    @Query("""
            SELECT COALESCE(SUM(i.outstanding), 0), COUNT(i)
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND i.outstanding IS NOT NULL
              AND i.outstanding > 0
            """)
    Object[] outstandingByType(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type);

    /**
     * Monthly aggregation by year + month over invoiceDate.
     * Returns rows of (year, month, totalAmount).
     */
    @Query("""
            SELECT YEAR(i.invoiceDate), MONTH(i.invoiceDate), COALESCE(SUM(i.amount), 0)
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND i.invoiceDate IS NOT NULL
              AND (:from IS NULL OR i.invoiceDate >= :from)
              AND (:to IS NULL OR i.invoiceDate <= :to)
            GROUP BY YEAR(i.invoiceDate), MONTH(i.invoiceDate)
            ORDER BY YEAR(i.invoiceDate), MONTH(i.invoiceDate)
            """)
    List<Object[]> monthlyByType(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Returns rows of (dueDate, outstanding) for open invoices of a given type.
     * Bucketing into aging ranges is done in Java to avoid DB-dialect-specific
     * date arithmetic.
     */
    @Query("""
            SELECT i.dueDate, i.outstanding
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND i.outstanding IS NOT NULL
              AND i.outstanding > 0
            """)
    List<Object[]> openInvoicesForAging(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type);

    /**
     * Top parties (customers or vendors) by total invoice amount in window.
     * Returns rows of (toParty, totalAmount, totalOutstanding, invoiceCount).
     */
    @Query("""
            SELECT COALESCE(NULLIF(TRIM(i.toParty), ''), 'Unknown'),
                   COALESCE(SUM(i.amount), 0),
                   COALESCE(SUM(i.outstanding), 0),
                   COUNT(i)
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND (:from IS NULL OR i.invoiceDate >= :from)
              AND (:to IS NULL OR i.invoiceDate <= :to)
            GROUP BY COALESCE(NULLIF(TRIM(i.toParty), ''), 'Unknown')
            ORDER BY COALESCE(SUM(i.amount), 0) DESC
            """)
    List<Object[]> topPartiesByType(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("""
            SELECT COUNT(i)
            FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.archived = false
              AND (:from IS NULL OR i.invoiceDate >= :from)
              AND (:to IS NULL OR i.invoiceDate <= :to)
            """)
    long countInvoicesBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ======================================================
    //  Dashboard aggregations
    // ======================================================

    /** Open (outstanding > 0) invoices of a type, oldest due date first, for "top overdue/due" widgets. */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.company.id = :companyId
              AND i.type = :type
              AND i.archived = false
              AND i.outstanding IS NOT NULL
              AND i.outstanding > 0
            ORDER BY i.dueDate ASC
            """)
    List<Invoice> findOpenInvoicesByTypeOrderByDueDateAsc(
            @Param("companyId") Long companyId,
            @Param("type") InvoiceType type,
            Pageable pageable);

    /** Invoices of a type raised within [from, to], for "payment status this month" bucketing. */
    List<Invoice> findByCompany_IdAndTypeAndArchivedFalseAndInvoiceDateBetween(
            Long companyId, InvoiceType type, LocalDate from, LocalDate to);
}
