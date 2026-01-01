package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "employee_passports",
        uniqueConstraints = @UniqueConstraint(columnNames = "employee_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Passport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String passportNo;

    @Column(nullable = false)
    private String nameAsPassport;

    @Column(nullable = false)
    private String issueCountry;

    @Column(nullable = false)
    private String nationality;

    private LocalDate issueDate;
    private LocalDate expiryDate;
}
