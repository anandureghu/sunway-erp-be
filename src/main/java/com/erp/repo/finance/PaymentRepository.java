package com.erp.repo.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentCode(String code);
    List<Payment> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Payment> findByCompany_IdAndPaymentDirectionOrderByCreatedAtDesc(Long companyId, PaymentDirection paymentDirection);
    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(String invoiceId);

    Optional<Payment> findFirstByPurchaseOrderIdAndPaymentDirection(
            Long purchaseOrderId, PaymentDirection paymentDirection);

    // ======================================================
    //  Finance report aggregations
    // ======================================================

    /** Sum of payment amounts in [from, to] for a given direction, scoped to company. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0), COUNT(p)
            FROM Payment p
            WHERE p.company.id = :companyId
              AND p.paymentDirection = :direction
              AND p.archived = false
              AND (:from IS NULL OR p.effectiveDate >= :from)
              AND (:to IS NULL OR p.effectiveDate <= :to)
            """)
    Object[] sumByDirectionBetween(
            @Param("companyId") Long companyId,
            @Param("direction") PaymentDirection direction,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Monthly aggregation by year + month over effectiveDate.
     * Returns rows of (year, month, totalAmount).
     */
    @Query("""
            SELECT YEAR(p.effectiveDate), MONTH(p.effectiveDate), COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.company.id = :companyId
              AND p.paymentDirection = :direction
              AND p.archived = false
              AND p.effectiveDate IS NOT NULL
              AND (:from IS NULL OR p.effectiveDate >= :from)
              AND (:to IS NULL OR p.effectiveDate <= :to)
            GROUP BY YEAR(p.effectiveDate), MONTH(p.effectiveDate)
            ORDER BY YEAR(p.effectiveDate), MONTH(p.effectiveDate)
            """)
    List<Object[]> monthlyByDirection(
            @Param("companyId") Long companyId,
            @Param("direction") PaymentDirection direction,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.company.id = :companyId
              AND p.archived = false
              AND (:from IS NULL OR p.effectiveDate >= :from)
              AND (:to IS NULL OR p.effectiveDate <= :to)
            """)
    BigDecimal sumAllBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
