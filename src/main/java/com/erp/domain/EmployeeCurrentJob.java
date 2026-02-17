package com.erp.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
@Getter
@Entity
@Table(name = "employee_current_job")
public class EmployeeCurrentJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Setter
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;
    @Setter
    private String jobCode;
    @Setter
    private String jobTitle;
    @Setter
    private String departmentCode;
    @Setter
    private String departmentName;
    @Setter
    private String jobLevel;
    @Setter
    private String grade;
    @Setter
    private LocalDate startDate;
    @Setter
    private LocalDate effectiveFrom;
    @Setter
    private LocalDate expectedEndDate;
    @Setter
    private String workLocation;
    @Setter
    private String workCity;
    @Setter
    private String workCountry;
}
