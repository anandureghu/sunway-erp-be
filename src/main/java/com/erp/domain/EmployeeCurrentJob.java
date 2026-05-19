package com.erp.domain;

import com.erp.domain.enums.EmploymentCategory;
import com.erp.domain.enums.EmploymentType;
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

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_code_id", nullable = false)
    private JobCode jobCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    private LocalDate startDate;
    private LocalDate effectiveFrom;
    private LocalDate expectedEndDate;

    private String workLocation;
    private String workCity;
    private String workCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_category", length = 30)
    private EmploymentCategory employmentCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;
}
