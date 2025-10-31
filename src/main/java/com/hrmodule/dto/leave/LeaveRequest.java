// src/main/java/com/hrmodule/dto/leave/LeaveRequest.java
package com.hrmodule.dto.leave;

import java.time.LocalDate;

public class LeaveRequest {
    private Long employeeId;
    private String leaveType;
    private String leaveStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;
    private Integer leaveBalance; // optional snapshot sent from FE

    // getters/setters
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
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
    public Integer getLeaveBalance() { return leaveBalance; }
    public void setLeaveBalance(Integer leaveBalance) { this.leaveBalance = leaveBalance; }
}
