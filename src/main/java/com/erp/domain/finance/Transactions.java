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
@Table(name = "transactions")
public class Transactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionType;
    private String fiscalType;
    private BigDecimal postedAmount;
    private BigDecimal amount;
    private Instant postedDate;
    private Instant schedulePostingDate;
    private Instant transactionDate;

    @ManyToOne
    @JoinColumn(name = "debit_account")
    private ChartOfAccounts debitAccount;

    @ManyToOne
    @JoinColumn(name = "credit_account")
    private ChartOfAccounts creditAccount;

    @ManyToOne
    @JoinColumn(name = "invoice_id")
    private Invoices invoice;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = true)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = true)
    private Vendor vendor;

    private String transactionDescription;
    private Boolean isPosted = false;
    private Instant createdAt;
}

