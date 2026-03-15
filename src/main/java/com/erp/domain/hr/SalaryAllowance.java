package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "salary_allowances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryAllowance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allowance_type_id", nullable = true)  // ✅ nullable now
    private AllowanceType allowanceType;

    @Column(nullable = false)
    private BigDecimal amount;

    private LocalDate effectiveDate;

    private String note;

    // ✅ add this — for manual allowances without a type, store name directly
    @Column(name = "custom_name")
    private String customName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;
}