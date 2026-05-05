package com.erp.dto.timesheet;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceHistoryItemResponse {

    private LocalDate attendanceDate;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Long workedMinutes;
    private String workedDuration;
    private String status;

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
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
}