package com.erp.domain.sales;

import com.erp.domain.User;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.BankAccount;
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
@Table(
        name = "sales_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sales_orders_company_order_number", columnNames = {"company_id", "order_number"})
        }
)
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false)
    private LocalDate invoiceDueDate;

    @Column(length = 1000)
    private String shippingAddress;

    @Column(nullable = false)
    private String status; // QUOTATION, CONFIRMED, COMPLETED, CANCELLED

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "debit_account_id")
    private ChartOfAccounts debitAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_account_id")
    private ChartOfAccounts creditAccount;

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
