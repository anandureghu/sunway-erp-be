package com.erp.domain.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_variances")
public class StockVariance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_warehouse_id")
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_warehouse_id")
    private Warehouse toWarehouse;

    @Column(name = "variance_type", nullable = false, length = 50)
    private String varianceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "variance_status", nullable = false, length = 30)
    private StockVarianceStatus varianceStatus;

    @Column(name = "adjustment_mode", nullable = false, length = 20)
    private String adjustmentMode;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column(name = "adjustment_quantity")
    private Integer adjustmentQuantity;

    @Column(name = "transfer_quantity")
    private Integer transferQuantity;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "variance_date", nullable = false)
    private LocalDate varianceDate;

    @Column(name = "finance_transaction_id")
    private Long financeTransactionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
