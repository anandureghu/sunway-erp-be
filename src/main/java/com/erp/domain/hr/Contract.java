package com.erp.domain.hr;

import com.erp.domain.Employee;
import com.erp.domain.enums.ContractStatus;
import com.erp.domain.enums.ContractType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "contracts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contract_code", columnNames = "contract_code")
        }
)
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "contract_seq")
    @SequenceGenerator(
            name = "contract_seq",
            sequenceName = "contract_sequence",
            allocationSize = 1
    )
    private Long id;

    @Column(name = "contract_code", nullable = false, updatable = false)
    private String contractCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    private Integer contractPeriodMonths;

    private Integer noticePeriodDays;

    private String salaryRateType;

    private LocalDate signatureDate;

    private String signedBy;

    private String attachmentPath;

    // Soft delete
    @Column(nullable = false)
    private boolean deleted = false;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 🔗 Many contracts belong to one employee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // 🔗 One contract has many allowances
    @OneToMany(mappedBy = "contract",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<SalaryAllowance> allowances = new ArrayList<>();
}