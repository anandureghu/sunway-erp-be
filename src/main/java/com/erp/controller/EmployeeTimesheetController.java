package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.AttendanceHistoryItemResponse;
import com.erp.dto.timesheet.MonthlySummaryResponse;
import com.erp.dto.timesheet.TimesheetDashboardResponse;
import com.erp.dto.timesheet.TimesheetTodayResponse;
import com.erp.service.EmployeeTimesheetService;
import com.erp.service.security.annotation.RequiresPermission;
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

    // Self-service attendance: any authenticated employee may punch their OWN
    // shift (enforced in the service via assertSelfServiceWrite); an HR user needs
    // the HR_REPORTS *_ALL grant to punch on someone else's behalf. No coarse
    // @RequiresPermission here, since that would block ordinary employees.
    @PostMapping("/checkin")
    public ResponseEntity<TimesheetTodayResponse> checkIn(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.checkIn(employeeId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<TimesheetTodayResponse> checkOut(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.checkOut(employeeId));
    }

    // HR_REPORTS — VIEW_OWN for the employee viewing their own attendance,
    // VIEW_ALL for HR/admins viewing anyone's.
    @RequiresPermission(module = AppModule.HR_REPORTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/today")
    public ResponseEntity<TimesheetTodayResponse> getToday(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.getToday(employeeId));
    }

    @RequiresPermission(module = AppModule.HR_REPORTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/refresh")
    public ResponseEntity<TimesheetTodayResponse> refresh(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.getToday(employeeId));
    }

    @RequiresPermission(module = AppModule.HR_REPORTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
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

    @RequiresPermission(module = AppModule.HR_REPORTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
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

    @RequiresPermission(module = AppModule.HR_REPORTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
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