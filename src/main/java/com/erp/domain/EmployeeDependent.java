package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "employee_dependents")
public class EmployeeDependent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(length = 50, nullable = false)
    private String firstName;

    @Column(length = 50)
    private String middleName;

    @Column(length = 50, nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 50)
    private String nationality;

    @Column(length = 50)
    private String nationalId;

    @Column(length = 50)
    private String maritalStatus;

    @Column(length = 50)
    private String relationship;

    // ✅ Contact Information

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 150)
    private String addressLine1;

    @Column(length = 150)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;
}