// src/main/java/com/hrmodule/domain/Leave.java
package com.erp.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "leaves")
public class Leave {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(length = 30) private String leaveCode;          // e.g. L001
    @Column(length = 40) private String leaveType;          // Annual, Sick, ...
    @Column(length = 20) private String leaveStatus;        // Pending/Approved/Rejected/Cancelled

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;

    private Integer totalDaysOnVacation;                    // computed (business days)
    private Integer leaveBalance;                           // snapshot after this request (optional)

    // getters/setters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getLeaveCode() { return leaveCode; }
    public void setLeaveCode(String leaveCode) { this.leaveCode = leaveCode; }
    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
    public String getLeaveStatus() { return leaveStatus; }
    public void setLeaveStatus(String leaveStatus) { this.leaveStatus = leaveStatus; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDate getDateReported() { return dateReported; }
    public void setDateReported(LocalDate dateReported) { this.dateReported = dateReported; }
    public Integer getTotalDaysOnVacation() { return totalDaysOnVacation; }
    public void setTotalDaysOnVacation(Integer totalDaysOnVacation) { this.totalDaysOnVacation = totalDaysOnVacation; }
    public Integer getLeaveBalance() { return leaveBalance; }
    public void setLeaveBalance(Integer leaveBalance) { this.leaveBalance = leaveBalance; }
}
