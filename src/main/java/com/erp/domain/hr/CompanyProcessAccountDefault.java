package com.erp.domain.hr;

import com.erp.domain.finance.AccountingProcessCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "company_process_account_defaults",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "process_code"})
)
public class CompanyProcessAccountDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_code", nullable = false, length = 64)
    private AccountingProcessCode processCode;

    @Column(name = "debit_account_id")
    private Long debitAccountId;

    @Column(name = "credit_account_id")
    private Long creditAccountId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
