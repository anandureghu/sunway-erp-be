package com.erp.controller;

import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.service.LeaveService;
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

    /* ========= GET LEAVE INFO (for page load) ========= */
    @GetMapping
    public ResponseEntity<List<LeaveHistoryDTO>> getLeaves(
            @PathVariable("employeeId") Long employeeId) {
        return ResponseEntity.ok(leaveService.history(employeeId));
    }

    @GetMapping("/preview")
    public ResponseEntity<LeavePreviewDTO> preview(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("leaveType") String leaveType,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                leaveService.previewLeave(employeeId, leaveType, startDate, endDate)
        );
    }

    @PostMapping
    public ResponseEntity<LeaveHistoryDTO> apply(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody LeaveRequestDTO dto
    ) {
        LeaveHistoryDTO result = leaveService.applyLeave(employeeId, dto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<LeaveHistoryDTO>> history(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(leaveService.history(employeeId));
    }

}