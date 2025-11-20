package com.erp.domain.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.erp.domain.inventory.Customer;
import com.erp.domain.inventory.Vendor;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class Payments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long paymentCode;

    private String paymentMethodType;
    private BigDecimal amount;
    private Instant paymentDate;
    private Instant effectiveDate;
    private String paymentStatus;
    private String notesRemarks;
    private BigDecimal totalDue;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = true)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = true)
    private Vendor vendor;

    @Column(name = "created_at")
    private Instant createdAt;
}
