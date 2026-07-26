package com.erp.dto.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TimesheetTodayResponse {

    private Long employeeId;
    private LocalDate date;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Long workedMinutes;
    private String workedDuration;
    private String status;
    /** Company policy: whether punch in/out is used at all (drives the UI). */
    private Boolean requireCheckIn;
    private Double standardWorkingHoursPerDay;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public Long getWorkedMinutes() {
        return workedMinutes;
    }

    public void setWorkedMinutes(Long workedMinutes) {
        this.workedMinutes = workedMinutes;
    }

    public String getWorkedDuration() {
        return workedDuration;
    }

    public void setWorkedDuration(String workedDuration) {
        this.workedDuration = workedDuration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getRequireCheckIn() {
        return requireCheckIn;
    }

    public void setRequireCheckIn(Boolean requireCheckIn) {
        this.requireCheckIn = requireCheckIn;
    }

    public Double getStandardWorkingHoursPerDay() {
        return standardWorkingHoursPerDay;
    }

    public void setStandardWorkingHoursPerDay(Double standardWorkingHoursPerDay) {
        this.standardWorkingHoursPerDay = standardWorkingHoursPerDay;
    }
}