package com.erp.repo.finance;

import com.erp.domain.finance.Payment;
import com.erp.domain.finance.PaymentDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Payment> findByCompany_IdAndArchivedTrueAndPaymentDirectionOrderByCreatedAtDesc(
            Long companyId, PaymentDirection paymentDirection, Pageable pageable);

    List<Payment> findByCompany_IdAndArchivedTrueAndPaymentDirection(
            Long companyId, PaymentDirection paymentDirection);
    List<Payment> findByCompany_IdAndInvoiceIdOrderByCreatedAtDesc(Long companyId, String invoiceId);

    Optional<Payment> findFirstByPurchaseOrderIdAndPaymentDirection(
            Long purchaseOrderId, PaymentDirection paymentDirection);

    List<Payment> findByPurchaseOrderIdAndPaymentDirectionOrderByCreatedAtDesc(
            Long purchaseOrderId, PaymentDirection paymentDirection);

    boolean existsByCompany_IdAndInvoiceIdAndPaymentMethod(
            Long companyId, String invoiceId, String paymentMethod);

    Optional<Payment> findFirstByCompany_IdAndInvoiceIdAndPaymentMethod(
            Long companyId, String invoiceId, String paymentMethod);

    boolean existsByPurchaseOrderIdAndPaymentDirectionAndPaymentMethod(
            Long purchaseOrderId, PaymentDirection paymentDirection, String paymentMethod);

    Optional<Payment> findFirstByPurchaseOrderIdAndPaymentDirectionAndPaymentMethod(
            Long purchaseOrderId, PaymentDirection paymentDirection, String paymentMethod);

    @Query("""
            SELECT COUNT(p) FROM Payment p, PurchaseOrder po
            WHERE p.purchaseOrderId = po.id
              AND po.supplier.id = :supplierId
              AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.VENDOR
              AND UPPER(p.paymentMethod) = 'PENDING_VENDOR_PAYMENT'
              AND p.archived = false
            """)
    long countPendingVendorPaymentsForSupplier(@Param("supplierId") Long supplierId);

    @Query("""
            SELECT p.paymentCode FROM Payment p, PurchaseOrder po
            WHERE p.purchaseOrderId = po.id
              AND po.supplier.id = :supplierId
              AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.VENDOR
              AND UPPER(p.paymentMethod) = 'PENDING_VENDOR_PAYMENT'
              AND p.archived = false
            ORDER BY p.createdAt DESC
            """)
    List<String> findPendingVendorPaymentCodesForSupplier(@Param("supplierId") Long supplierId);

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

    /** Company-wide count of vendor payments awaiting approval, for the dashboard pending-approvals widget. */
    @Query("""
            SELECT COUNT(p) FROM Payment p
            WHERE p.company.id = :companyId
              AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.VENDOR
              AND UPPER(p.paymentMethod) = 'PENDING_VENDOR_PAYMENT'
              AND p.archived = false
            """)
    long countPendingVendorPaymentsForCompany(@Param("companyId") Long companyId);

    /**
     * Sum of confirmed (non-pending, non-archived) payment amounts applied to an invoice,
     * scoped to the invoice's company so identical invoice codes across tenants do not mix.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.company.id = :companyId
              AND p.invoiceId = :invoiceId
              AND p.archived = false
              AND UPPER(p.paymentMethod) NOT IN ('PENDING_REQUEST', 'PENDING_VENDOR_PAYMENT')
            """)
    BigDecimal sumConfirmedAmountByCompanyIdAndInvoiceId(
            @Param("companyId") Long companyId, @Param("invoiceId") String invoiceId);
}
