package com.erp.domain.sales;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
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
@Table(name = "sales_orders")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false)
    private String status; // DRAFT, CONFIRMED, CANCELLED

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    private Instant createdAt;

    // 🔑 Lines live INSIDE SalesOrder
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sales_order_id") // FK in same aggregate
    private List<SalesOrderItem> items;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
