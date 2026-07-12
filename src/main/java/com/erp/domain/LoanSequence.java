package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "loan_sequence",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_loan_sequence_company_loan_type", columnNames = {"company_id", "loan_type"})
        }
)
@Getter
@Setter
public class LoanSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Column(nullable = false)
    private Long currentSequence = 0L;
}