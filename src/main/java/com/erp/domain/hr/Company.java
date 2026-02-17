package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", length = 50, nullable = false)
    private String companyName;

    @Column(name = "no_of_employees", length = 20)
    private String noOfEmployees;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency")
    private Currency currency;

    @Column(name = "cr_no")
    private Long crNo;

    @Column(name = "computer_card", length = 20)
    private String computerCard;

    @Column(length = 50)
    private String street;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 50)
    private String country;

    @Column(name = "phone_no", length = 20)
    private String phoneNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "is_hr_enabled", nullable = false)
    private boolean hrEnabled;

    @Column(name = "is_finance_enabled", nullable = false)
    private boolean financeEnabled;

    @Column(name = "is_inventory_enabled", nullable = false)
    private boolean inventoryEnabled;
}
