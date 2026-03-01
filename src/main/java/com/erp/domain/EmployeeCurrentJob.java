package com.erp.domain;

import com.erp.domain.hrsettings.JobCode;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "employee_current_job")
public class EmployeeCurrentJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ EAGER — employee must always be loaded with the job
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    // ✅ EAGER — jobCode must always be loaded with the job
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_code_id", nullable = false)
    private JobCode jobCode;

    // ✅ EAGER — department must always be loaded with the job
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    private LocalDate startDate;
    private LocalDate effectiveFrom;
    private LocalDate expectedEndDate;

    private String workLocation;
    private String workCity;
    private String workCountry;
}