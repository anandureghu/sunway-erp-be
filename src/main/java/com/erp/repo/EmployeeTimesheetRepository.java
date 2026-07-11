package com.erp.repo;


import com.erp.domain.EmployeeTimesheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTimesheetRepository extends JpaRepository<EmployeeTimesheet, Long> {

    Optional<EmployeeTimesheet> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

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