package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "employee_experience")
public class EmployeeExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    private String companyName;
    private String jobTitle;
    private LocalDate lastDateWorked;
    private Integer numberOfYears;

    @Column(columnDefinition = "TEXT")
    private String companyAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;
}


