package com.erp.controller;

import com.erp.dto.timesheet.AttendanceHistoryItemResponse;
import com.erp.dto.timesheet.MonthlySummaryResponse;
import com.erp.dto.timesheet.TimesheetDashboardResponse;
import com.erp.dto.timesheet.TimesheetTodayResponse;
import com.erp.service.EmployeeTimesheetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/timesheet")
public class EmployeeTimesheetController {

    private final EmployeeTimesheetService service;

    public EmployeeTimesheetController(EmployeeTimesheetService service) {
        this.service = service;
    }

    @PostMapping("/checkin")
    public ResponseEntity<TimesheetTodayResponse> checkIn(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.checkIn(employeeId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<TimesheetTodayResponse> checkOut(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.checkOut(employeeId));
    }

    @GetMapping("/today")
    public ResponseEntity<TimesheetTodayResponse> getToday(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.getToday(employeeId));
    }

    @GetMapping("/refresh")
    public ResponseEntity<TimesheetTodayResponse> refresh(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.getToday(employeeId));
    }

    @GetMapping("/month-summary")
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        int resolvedMonth = month != null ? month : now.getMonthValue();

        return ResponseEntity.ok(service.getMonthlySummary(employeeId, resolvedYear, resolvedMonth));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AttendanceHistoryItemResponse>> getAttendanceHistory(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        int resolvedMonth = month != null ? month : now.getMonthValue();

        return ResponseEntity.ok(service.getAttendanceHistory(employeeId, resolvedYear, resolvedMonth));
    }

    @GetMapping
    public ResponseEntity<TimesheetDashboardResponse> getDashboard(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int resolvedYear = year != null ? year : now.getYear();
        int resolvedMonth = month != null ? month : now.getMonthValue();

        return ResponseEntity.ok(service.getDashboard(employeeId, resolvedYear, resolvedMonth));
    }
}