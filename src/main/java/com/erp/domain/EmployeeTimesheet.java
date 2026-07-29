package com.erp.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_timesheets")
public class EmployeeTimesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;

    private Long workedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimesheetStatus status = TimesheetStatus.NOT_CHECKED_IN;

    /** True when the nightly job closed a session the employee forgot to check out of. */
    @Column(name = "auto_checked_out", nullable = false)
    private boolean autoCheckedOut = false;

    /** Human-readable annotation (e.g. the auto-checkout explanation). */
    @Column(name = "note", length = 255)
    private String note;

    public void calculateWorkedMinutes() {
        if (checkInTime != null && checkOutTime != null) {
            this.workedMinutes = Duration.between(checkInTime, checkOutTime).toMinutes();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }

    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }

    public Long getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(Long workedMinutes) { this.workedMinutes = workedMinutes; }

    public TimesheetStatus getStatus() { return status; }
    public void setStatus(TimesheetStatus status) { this.status = status; }

    public boolean isAutoCheckedOut() { return autoCheckedOut; }
    public void setAutoCheckedOut(boolean autoCheckedOut) { this.autoCheckedOut = autoCheckedOut; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}