package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Setter
@Getter
@Entity
@Table(name = "employee_leaves")
public class EmployeeLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String leaveCode;
    private String leaveType;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;

    private Integer totalDays;

    private String leaveStatus; // APPROVED

}
