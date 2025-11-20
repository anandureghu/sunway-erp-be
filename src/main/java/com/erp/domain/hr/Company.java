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

    @Column(name = "company_name", length = 30)
    private String companyName;

    @Column(name = "no_of_employees", length = 20)
    private String noOfEmployees;

    @Column(name = "cr_no")
    private Long crNo;

    @Column(name = "computer_card", length = 20)
    private String computerCard;

    @Column(length = 20)
    private String street;

    @Column(length = 20)
    private String city;

    @Column(length = 20)
    private String state;

    @Column(length = 20)
    private String country;

    @Column(name = "phone_no", length = 20)
    private String phoneNo;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "is_hr_enabled")
    private boolean hrEnabled;

    @Column(name = "is_finance_enabled")
    private boolean financeEnabled;

    @Column(name = "is_inventory_enabled")
    private boolean inventoryEnabled;
}
