package com.erp.controller;

import com.erp.dto.timesheet.AttendanceArchiveRowDTO;
import com.erp.dto.timesheet.AttendanceHistoryPageDTO;
import com.erp.dto.timesheet.EmployeeMonthlyAttendanceDTO;
import com.erp.service.AttendanceArchiveService;
import com.erp.service.AttendanceReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * HR attendance reporting — the company-wide monthly worked-days view plus the
 * month-archive lifecycle. Scope/permission is enforced in the services
 * (HR_REPORTS grant → all employees, otherwise self for the live summary).
 */
@RestController
@RequestMapping("/api/hr/attendance")
public class HrAttendanceReportController {

    private final AttendanceReportService service;
    private final AttendanceArchiveService archiveService;

    public HrAttendanceReportController(
            AttendanceReportService service,
            AttendanceArchiveService archiveService) {
        this.service = service;
        this.archiveService = archiveService;
    }

    @GetMapping("/monthly-summary")
    public ResponseEntity<List<EmployeeMonthlyAttendanceDTO>> getMonthlySummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        int resolvedMonth = month != null ? month : now.getMonthValue();

        return ResponseEntity.ok(service.getMonthlySummary(resolvedYear, resolvedMonth));
    }

    @PostMapping("/archive")
    public ResponseEntity<Map<String, Object>> archiveMonth(
            @RequestParam int year,
            @RequestParam int month
    ) {
        int count = archiveService.archiveMonth(year, month);
        return ResponseEntity.ok(Map.of(
                "archivedCount", count,
                "year", year,
                "month", month));
    }

    @DeleteMapping("/archive")
    public ResponseEntity<Void> unarchiveMonth(
            @RequestParam int year,
            @RequestParam int month
    ) {
        archiveService.unarchiveMonth(year, month);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/archive")
    public ResponseEntity<List<AttendanceArchiveRowDTO>> listArchived(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String employeeCode
    ) {
        return ResponseEntity.ok(archiveService.listArchived(year, month, employeeCode));
    }

    /** Paged worked-days history for a month (archived snapshot or live figures). */
    @GetMapping("/history")
    public ResponseEntity<AttendanceHistoryPageDTO> getMonthlyHistory(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                archiveService.getMonthlyHistory(year, month, employeeCode, page, size));
    }
}
