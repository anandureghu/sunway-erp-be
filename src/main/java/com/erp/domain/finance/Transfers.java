package com.erp.domain.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.erp.domain.Employee; // optional link if HR module exists
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "transfers")
public class Transfers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String transferCode;

    private String transferType;
    private BigDecimal transferAmount;
    private String transferReason;
    private String description;

    @ManyToOne
    @JoinColumn(name = "debit_account")
    private ChartOfAccounts debitAccount;

    @ManyToOne
    @JoinColumn(name = "credit_account")
    private ChartOfAccounts creditAccount;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = true)
    private Employee createdBy;

    private Instant transferDate;
    private Instant createdAt;
}
