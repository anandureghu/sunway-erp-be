package com.erp.domain.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Vendor;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "purchase_orders",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"company_id", "order_number"})
        }
)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "supplier_id")
    private Vendor supplier;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "purchase_order_id")
    private List<PurchaseOrderItem> items;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @ManyToOne
    @JoinColumn(name = "source_requisition_id")
    private PurchaseRequisition sourceRequisition;
}
