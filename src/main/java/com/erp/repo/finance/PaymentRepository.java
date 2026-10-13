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

    /** Sum of confirmed payment amounts in [from, to] for a given direction (excludes pending). */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0), COUNT(p)
            FROM Payment p
            WHERE p.company.id = :companyId
              AND p.paymentDirection = :direction
              AND p.archived = false
              AND UPPER(COALESCE(p.paymentMethod, '')) NOT IN ('PENDING_REQUEST', 'PENDING_VENDOR_PAYMENT')
              AND (:from IS NULL OR p.effectiveDate >= :from)
              AND (:to IS NULL OR p.effectiveDate <= :to)
            """)
    Object[] sumByDirectionBetween(
            @Param("companyId") Long companyId,
            @Param("direction") PaymentDirection direction,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Monthly aggregation by year + month over effectiveDate (confirmed payments only).
     * Returns rows of (year, month, totalAmount).
     */
    @Query("""
            SELECT YEAR(p.effectiveDate), MONTH(p.effectiveDate), COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.company.id = :companyId
              AND p.paymentDirection = :direction
              AND p.archived = false
              AND UPPER(COALESCE(p.paymentMethod, '')) NOT IN ('PENDING_REQUEST', 'PENDING_VENDOR_PAYMENT')
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

    /**
     * Company payment list with optional direction, outstanding/completed tab, and text search.
     * {@code listTab}: {@code outstanding} (pending confirm) or {@code completed} (settled / void-style).
     * Vendor rows require a PO in CONFIRMED / PARTIALLY_RECEIVED / RECEIVED.
     */
    @Query("""
            SELECT DISTINCT p FROM Payment p
            LEFT JOIN PurchaseOrder po ON po.id = p.purchaseOrderId
            LEFT JOIN po.supplier supplier
            LEFT JOIN Invoice inv ON inv.company.id = p.company.id AND inv.invoiceId = p.invoiceId
            LEFT JOIN SalesOrder so ON so.id = inv.orderId AND inv.type = com.erp.domain.InvoiceType.SALES
            LEFT JOIN so.customer customer
            WHERE p.company.id = :companyId
              AND p.archived = false
              AND (
                    :direction IS NULL
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.CUSTOMER
                         AND (p.paymentDirection IS NULL
                              OR p.paymentDirection = com.erp.domain.finance.PaymentDirection.CUSTOMER)
                       )
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.VENDOR
                         AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.VENDOR
                         AND p.purchaseOrderId IS NOT NULL
                         AND po.status IN (
                              com.erp.domain.purchase.PurchaseOrderStatus.CONFIRMED,
                              com.erp.domain.purchase.PurchaseOrderStatus.PARTIALLY_RECEIVED,
                              com.erp.domain.purchase.PurchaseOrderStatus.RECEIVED
                         )
                       )
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.OTHER
                         AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.OTHER
                       )
                  )
              AND (
                    :listTab IS NULL
                    OR (
                         :listTab = 'outstanding'
                         AND UPPER(COALESCE(p.paymentMethod, '')) IN :pendingMethods
                       )
                    OR (
                         :listTab = 'completed'
                         AND UPPER(COALESCE(p.paymentMethod, '')) NOT IN :pendingMethods
                       )
                  )
              AND (
                    :q IS NULL OR :q = ''
                    OR LOWER(COALESCE(p.paymentCode, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(p.invoiceId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(p.paymentMethod, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(p.expenseCategory, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(p.payee, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(po.orderNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(supplier.vendorName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(so.orderNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(customer.customerName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR CAST(p.purchaseOrderId AS string) LIKE CONCAT('%', :q, '%')
                    OR CAST(p.amount AS string) LIKE CONCAT('%', :q, '%')
                  )
            """)
    Page<Payment> searchForCompany(
            @Param("companyId") Long companyId,
            @Param("direction") PaymentDirection direction,
            @Param("listTab") String listTab,
            @Param("pendingMethods") java.util.Collection<String> pendingMethods,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT p) FROM Payment p
            LEFT JOIN PurchaseOrder po ON po.id = p.purchaseOrderId
            WHERE p.company.id = :companyId
              AND p.archived = false
              AND (
                    :direction IS NULL
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.CUSTOMER
                         AND (p.paymentDirection IS NULL
                              OR p.paymentDirection = com.erp.domain.finance.PaymentDirection.CUSTOMER)
                       )
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.VENDOR
                         AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.VENDOR
                         AND p.purchaseOrderId IS NOT NULL
                         AND po.status IN (
                              com.erp.domain.purchase.PurchaseOrderStatus.CONFIRMED,
                              com.erp.domain.purchase.PurchaseOrderStatus.PARTIALLY_RECEIVED,
                              com.erp.domain.purchase.PurchaseOrderStatus.RECEIVED
                         )
                       )
                    OR (
                         :direction = com.erp.domain.finance.PaymentDirection.OTHER
                         AND p.paymentDirection = com.erp.domain.finance.PaymentDirection.OTHER
                       )
                  )
              AND (
                    (
                         :listTab = 'outstanding'
                         AND UPPER(COALESCE(p.paymentMethod, '')) IN :pendingMethods
                    )
                    OR (
                         :listTab = 'completed'
                         AND UPPER(COALESCE(p.paymentMethod, '')) NOT IN :pendingMethods
                    )
                  )
            """)
    long countForCompanyTab(
            @Param("companyId") Long companyId,
            @Param("direction") PaymentDirection direction,
            @Param("listTab") String listTab,
            @Param("pendingMethods") java.util.Collection<String> pendingMethods);
}
