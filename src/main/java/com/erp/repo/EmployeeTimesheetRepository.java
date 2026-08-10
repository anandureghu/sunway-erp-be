package com.erp.repo;


import com.erp.domain.EmployeeTimesheet;
import com.erp.domain.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTimesheetRepository extends JpaRepository<EmployeeTimesheet, Long> {

    Optional<EmployeeTimesheet> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    /** Open sessions (checked in, never checked out) on or before a given day — for the auto-checkout job. */
    List<EmployeeTimesheet> findByStatusAndAttendanceDateLessThanEqual(TimesheetStatus status, LocalDate date);

    /** Open sessions on a specific day — for the intraday max-shift auto-checkout sweep. */
    List<EmployeeTimesheet> findByStatusAndAttendanceDate(TimesheetStatus status, LocalDate date);

    List<EmployeeTimesheet> findByEmployeeIdAndAttendanceDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<EmployeeTimesheet> findByEmployeeIdInAndAttendanceDateBetween(
            List<Long> employeeIds,
            LocalDate startDate,
            LocalDate endDate
    );
}