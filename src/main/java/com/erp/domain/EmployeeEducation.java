package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employee_education")
public class EmployeeEducation {

    // ---------- getters ----------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------- setters ----------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private String schoolName;
    private String schoolAddress;
    private String degreeEarned;
    private String major;
    private Integer yearGraduated;

    @Column(columnDefinition = "TEXT")
    private String awards;

    @Column(columnDefinition = "TEXT")
    private String notes;

}
