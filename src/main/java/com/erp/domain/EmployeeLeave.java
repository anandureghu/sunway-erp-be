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

    /** Optional colleague (same department) who covers duties during the leave. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegate_employee_id")
    private Employee delegate;

    private String leaveCode;
    private String leaveType;

    private LocalDate startDate;
    private LocalDate endDate;

    /** Date the leave was applied/submitted (stamped server-side). */
    private LocalDate dateReported;

    /** Date the employee heads back to the office after the leave. Optional. */
    private LocalDate returnDate;

    private Integer totalDays;
    private Boolean includeWeekends;
    private String supportingDocumentPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus leaveStatus;

    /** Approver's reason when the leave is rejected. */
    @Column(length = 1000)
    private String rejectionComment;
}