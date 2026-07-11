package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A frozen snapshot of one employee's worked-days for one month, taken when HR
 * archives that month. Kept separate from the live {@link EmployeeTimesheet}
 * rows so the payroll-driving figures remain auditable after the fact.
 */
@Entity
@Table(name = "attendance_month_archive")
@Getter
@Setter
public class AttendanceMonthArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long employeeId;

    private String employeeNo;
    private String employeeName;
    private String department;

    @Column(nullable = false)
    private int periodYear;

    @Column(nullable = false)
    private int periodMonth;

    private int daysRecorded;
    private int daysPresent;
    private double totalHours;

    @Column(nullable = false)
    private LocalDateTime archivedAt;

    private Long archivedBy;
}
