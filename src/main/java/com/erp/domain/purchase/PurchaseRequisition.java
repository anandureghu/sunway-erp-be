package com.erp.domain.purchase;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.inventory.Warehouse;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_requisitions")
public class PurchaseRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requisitionNumber;

    private boolean archived = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseRequisitionStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @ManyToOne
    @JoinColumn(name = "preferred_supplier_id")
    private Vendor preferredSupplier;

    private String supplierAddress;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    private PurchaseRequisitionReviewAction reviewAction;

    private Instant rejectedAt;

    @ManyToOne
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    private Instant createdAt;
    private Instant approvedAt;

    private LocalDate requestedDate;
    private LocalDate requiredDeliveryDate;
    private String projectCode;

    private String requisitionDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PurchaseRequisitionUrgency urgency = PurchaseRequisitionUrgency.NORMAL;

    private LocalDate requiredByDate;

    @ManyToOne
    @JoinColumn(name = "delivery_warehouse_id")
    private Warehouse deliveryWarehouse;

    private String justification;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "requisition_id")
    private List<PurchaseRequisitionItem> items;

    private Instant convertedAt;

    @ManyToOne
    @JoinColumn(name = "converted_by")
    private User convertedBy;

    /** Expense / inventory / encumbrance leg (posting matches {@link com.erp.service.finance.TransactionService}). */
    @ManyToOne
    @JoinColumn(name = "debit_account_id")
    private ChartOfAccounts debitAccount;

    /** Offset leg (e.g. AP or clearing). */
    @ManyToOne
    @JoinColumn(name = "credit_account_id")
    private ChartOfAccounts creditAccount;

    /** Finance {@code transactions.id} created when vendor payment is confirmed in AP (GL posting). */
    @Column(name = "finance_transaction_id")
    private Long financeTransactionId;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (requestedDate == null) {
            requestedDate = LocalDate.now();
        }
        if (urgency == null) {
            urgency = PurchaseRequisitionUrgency.NORMAL;
        }
    }
}
