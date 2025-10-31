// src/main/java/com/hrmodule/dto/leave/LeaveResponse.java
package com.hrmodule.dto.leave;

import com.hrmodule.domain.Leave;

import java.time.LocalDate;

public class LeaveResponse {
    private Long id;
    private Long employeeId;
    private String leaveCode;
    private String leaveType;
    private String leaveStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dateReported;
    private Integer totalDaysOnVacation;
    private Integer leaveBalance;

    // ---- getters & setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

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

    // ---- mapper ----
    public static LeaveResponse from(Leave e) {
        LeaveResponse r = new LeaveResponse();
        r.setId(e.getId());
        r.setEmployeeId(e.getEmployee().getId());
        r.setLeaveCode(e.getLeaveCode());
        r.setLeaveType(e.getLeaveType());
        r.setLeaveStatus(e.getLeaveStatus());
        r.setStartDate(e.getStartDate());
        r.setEndDate(e.getEndDate());
        r.setDateReported(e.getDateReported());
        r.setTotalDaysOnVacation(e.getTotalDaysOnVacation());
        r.setLeaveBalance(e.getLeaveBalance());
        return r;
    }
}
