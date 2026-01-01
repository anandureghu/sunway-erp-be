package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "employee_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address_line1", nullable = false)
    private String line1;

    @Column(name = "address_line2")
    private String line2;

    private String city;
    private String state;
    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "address_type")
    private String addressType;

    @Column(name = "is_primary")
    private Boolean primaryAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
