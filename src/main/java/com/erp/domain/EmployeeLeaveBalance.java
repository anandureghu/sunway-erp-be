package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Entity
@Getter
@Table(
        name = "employee_leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type"})
)
public class EmployeeLeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "leave_type", nullable = false)
    private String leaveType;

    @Column(name = "total_leaves", nullable = false)
    private Integer totalLeaves;

    @Column(name = "remaining_leaves", nullable = false)
    private Integer remainingLeaves;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}