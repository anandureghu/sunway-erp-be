package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.service.LeaveService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/leaves")
@RequiredArgsConstructor
public class EmployeeLeaveController {

    private final LeaveService leaveService;

    // ======================================================
    // GET AVAILABLE LEAVE TYPES
    // VIEW_OWN — user sees their own available leave types
    // VIEW_ALL — admin/HR sees anyone's available types
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/available-types")
    public ResponseEntity<List<String>> getAvailableLeaveTypes(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                leaveService.getAvailableLeaveTypes(employeeId)
        );
    }

    // ======================================================
    // GET ALL LEAVES
    // VIEW_OWN — user views their own leave records
    // VIEW_ALL — admin/HR views anyone's leave records
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<LeaveHistoryDTO>> getLeaves(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                leaveService.history(employeeId)
        );
    }

    // ======================================================
    // PREVIEW LEAVE
    // VIEW_OWN — user previews their own leave calculation
    // VIEW_ALL — admin/HR previews for anyone
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/preview")
    public ResponseEntity<LeavePreviewDTO> preview(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("leaveType") String leaveType,
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        return ResponseEntity.ok(
                leaveService.previewLeave(employeeId, leaveType, startDate, endDate)
        );
    }

    // ======================================================
    // APPLY LEAVE
    // CREATE — applying for leave is a create operation
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<LeaveHistoryDTO> apply(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody LeaveRequestDTO dto) {

        return ResponseEntity.ok(
                leaveService.applyLeave(employeeId, dto)
        );
    }

    // ======================================================
    // LEAVE HISTORY
    // VIEW_OWN — user views their own leave history
    // VIEW_ALL — admin/HR views anyone's leave history
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/history")
    public ResponseEntity<List<LeaveHistoryDTO>> history(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                leaveService.history(employeeId)
        );
    }
}