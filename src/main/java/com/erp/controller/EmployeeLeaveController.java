package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.service.LeaveService;
import com.erp.service.security.PermissionCheckService;
import com.erp.security.context.AuthContext;
import com.erp.domain.security.Role;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.domain.Employee;
import com.erp.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees/{employeeId}/leaves")
@RequiredArgsConstructor
@Slf4j
public class EmployeeLeaveController {

    private final LeaveService leaveService;
    private final AuthContext authContext;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PermissionCheckService permissionCheckService;

    /* ═══════════════════════════════════════════════
       PERMISSION CHECK HELPER
    ═══════════════════════════════════════════════ */

    private void checkLeaveAccess(Long employeeId, HrAction action) {
        User authUser = getAuthUser();
        var auth = SecurityContextHolder.getContext().getAuthentication();

        // ✅ ADMIN/SUPER_ADMIN can do anything
        if (authUser.getRole() == Role.ADMIN || authUser.getRole() == Role.SUPER_ADMIN) {
            return;
        }

        // ✅ For regular users, check specific permissions
        Employee targetEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        boolean isOwnRecord = targetEmployee.getUser() != null
                && targetEmployee.getUser().getId().equals(authUser.getId());

        if (action == HrAction.VIEW_OWN || action == HrAction.CREATE) {
            if (!isOwnRecord) {
                throw new RuntimeException("Access denied: can only access your own leave records");
            }
        } else if (action == HrAction.VIEW_ALL) {
            boolean hasPermission = permissionCheckService.hasAccess(
                    auth,
                    HrModule.LEAVES,
                    HrAction.VIEW_ALL
            );
            if (!hasPermission) {
                throw new RuntimeException("Access denied: insufficient permissions");
            }
        }
    }

    /* ═══════════════════════════════════════════════
       GET AVAILABLE LEAVE TYPES
    ═══════════════════════════════════════════════ */

    @GetMapping("/available-types")
    public ResponseEntity<?> getAvailableLeaveTypes(
            @PathVariable("employeeId") Long employeeId) {

        try {
            checkLeaveAccess(employeeId, HrAction.VIEW_OWN);

            List<String> types = leaveService.getAvailableLeaveTypes(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "leaveTypes", types
            ));
        } catch (RuntimeException e) {
            log.warn("Access denied for getAvailableLeaveTypes: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       GET LEAVE HISTORY
    ═══════════════════════════════════════════════ */

    @GetMapping
    public ResponseEntity<?> getLeaves(
            @PathVariable("employeeId") Long employeeId) {

        try {
            checkLeaveAccess(employeeId, HrAction.VIEW_OWN);

            List<LeaveHistoryDTO> history = leaveService.history(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "leaves", history
            ));
        } catch (RuntimeException e) {
            log.warn("Access denied for getLeaves: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       PREVIEW LEAVE
    ═══════════════════════════════════════════════ */

    @GetMapping("/preview")
    public ResponseEntity<?> preview(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("leaveType") String leaveType,
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        try {
            // ✅ Validate dates
            if (startDate == null || endDate == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "startDate and endDate are required"
                ));
            }

            if (endDate.isBefore(startDate)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "End date cannot be before start date"
                ));
            }

            checkLeaveAccess(employeeId, HrAction.VIEW_OWN);

            LeavePreviewDTO preview = leaveService.previewLeave(
                    employeeId, leaveType, startDate, endDate);

            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for preview: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (RuntimeException e) {
            log.warn("Error previewing leave: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       APPLY FOR LEAVE
    ═══════════════════════════════════════════════ */

    @PostMapping
    public ResponseEntity<?> applyLeave(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody LeaveRequestDTO dto) {

        try {
            // ✅ Validate DTO
            if (dto.getLeaveType() == null || dto.getLeaveType().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Leave type is required"
                ));
            }

            if (dto.getStartDate() == null || dto.getEndDate() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Start date and end date are required"
                ));
            }

            checkLeaveAccess(employeeId, HrAction.CREATE);

            LeaveHistoryDTO history = leaveService.applyLeave(employeeId, dto);

            log.info("✅ Leave applied for employee {}: {} from {} to {}",
                    employeeId, dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

            return ResponseEntity.status(201).body(history);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for apply leave: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (RuntimeException e) {
            log.warn("Error applying leave: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       HISTORY (DUPLICATE - USE GET /api/...)
    ═══════════════════════════════════════════════ */

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @PathVariable("employeeId") Long employeeId) {

        try {
            checkLeaveAccess(employeeId, HrAction.VIEW_OWN);

            List<LeaveHistoryDTO> history = leaveService.history(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "history", history
            ));
        } catch (RuntimeException e) {
            log.warn("Access denied for history: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       AUTH HELPER
    ═══════════════════════════════════════════════ */

    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Unauthorized");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}