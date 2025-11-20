package com.erp.domain.finance;

import com.erp.domain.inventory.Vendor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.erp.domain.inventory.Customer;
import com.erp.domain.inventory.Orders;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoices")
public class Invoices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String invoiceId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;


    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @ManyToOne
    @JoinColumn(name = "order_no")
    private Orders order;

    private String status;
    private Instant dueDate;
    private Instant paidDate;

    private BigDecimal amount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;
    private BigDecimal interestRate;

    private String notesRemarks;
    private Instant createdAt;
}
