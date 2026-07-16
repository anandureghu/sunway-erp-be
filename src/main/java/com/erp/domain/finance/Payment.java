package com.erp.domain.finance;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_company_payment_code", columnNames = {"company_id", "payment_code"})
})
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_code", length = 64)
    private String paymentCode;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // e.g., BANK_TRANSFER, CASH

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    /** Portion of this payment's invoice reduction that came from an applied credit note, not cash. */
    @Column(name = "credit_applied_amount", precision = 18, scale = 2)
    private BigDecimal creditAppliedAmount;

    /** Set for {@link PaymentDirection#OTHER}: RENT, EMPLOYEE_REIMBURSEMENT, VENDOR_REIMBURSEMENT, UTILITIES, OTHER. */
    @Column(name = "expense_category", length = 64)
    private String expenseCategory;

    /** Set for {@link PaymentDirection#OTHER}: free text — who the expense was paid to. */
    @Column(name = "payee", length = 255)
    private String payee;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "invoice_id", length = 64)
    private String invoiceId; // invoice business id / code (sales) or null for vendor payables

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_direction", length = 20)
    @Builder.Default
    private PaymentDirection paymentDirection = PaymentDirection.CUSTOMER;

    /** Set when this row is a vendor payable tied to a purchase order. */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
