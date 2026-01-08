package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "loan_sequence")
@Getter
@Setter
public class LoanSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private LoanType loanType;

    @Column(nullable = false)
    private Long currentSequence = 0L;
}