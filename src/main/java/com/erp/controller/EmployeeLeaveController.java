package com.erp.controller;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.security.Role;
import com.erp.dto.leave.LeaveHistoryDTO;
import com.erp.dto.leave.LeavePreviewDTO;
import com.erp.dto.leave.LeaveRequestDTO;
import com.erp.dto.leave.LeaveReturnRequest;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class EmployeeLeaveController {

    private final LeaveService leaveService;
    private final AuthContext authContext;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @GetMapping("/employees/{employeeId}/leaves/available-types")
    public ResponseEntity<?> getAvailableLeaveTypes(@PathVariable Long employeeId) {
        try {
            validateSelfAccess(employeeId);
            List<String> types = leaveService.getAvailableLeaveTypes(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "leaveTypes", types
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/employees/{employeeId}/leaves")
    public ResponseEntity<?> getLeaves(@PathVariable Long employeeId) {
        try {
            validateSelfAccess(employeeId);
            List<LeaveHistoryDTO> leaves = leaveService.history(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "leaves", leaves
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/employees/{employeeId}/leaves/history")
    public ResponseEntity<?> history(@PathVariable Long employeeId) {
        try {
            validateSelfAccess(employeeId);
            List<LeaveHistoryDTO> history = leaveService.history(employeeId);
            return ResponseEntity.ok(Map.of(
                    "employeeId", employeeId,
                    "history", history
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/employees/{employeeId}/leaves/preview")
    public ResponseEntity<?> preview(
            @PathVariable Long employeeId,
            @RequestParam("leaveType") String leaveType,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "includeWeekends", defaultValue = "false") boolean includeWeekends) {
        try {
            validateSelfAccess(employeeId);

            LeavePreviewDTO preview = leaveService.previewLeave(
                    employeeId,
                    leaveType,
                    startDate,
                    endDate,
                    includeWeekends
            );

            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/employees/{employeeId}/leaves")
    public ResponseEntity<?> applyLeave(
            @PathVariable Long employeeId,
            @Valid @RequestBody LeaveRequestDTO dto) {
        return applyLeaveInternal(employeeId, dto, null);
    }

    @PostMapping(value = "/employees/{employeeId}/leaves", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> applyLeaveMultipart(
            @PathVariable Long employeeId,
            @RequestPart("data") LeaveRequestDTO dto,
            @RequestPart(value = "supportingDocument", required = false) MultipartFile supportingDocument) {
        return applyLeaveInternal(employeeId, dto, supportingDocument);
    }

    @PutMapping("/employees/{employeeId}/leaves/{leaveId}")
    public ResponseEntity<?> updateLeave(
            @PathVariable Long employeeId,
            @PathVariable Long leaveId,
            @Valid @RequestBody LeaveRequestDTO dto) {
        return updateLeaveInternal(employeeId, leaveId, dto, null);
    }

    @PutMapping(value = "/employees/{employeeId}/leaves/{leaveId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateLeaveMultipart(
            @PathVariable Long employeeId,
            @PathVariable Long leaveId,
            @RequestPart("data") LeaveRequestDTO dto,
            @RequestPart(value = "supportingDocument", required = false) MultipartFile supportingDocument) {
        return updateLeaveInternal(employeeId, leaveId, dto, supportingDocument);
    }

    private ResponseEntity<?> updateLeaveInternal(
            Long employeeId,
            Long leaveId,
            LeaveRequestDTO dto,
            MultipartFile supportingDocument) {
        try {
            validateSelfAccess(employeeId);
            LeaveHistoryDTO updatedLeave = leaveService.updateLeave(employeeId, leaveId, dto, supportingDocument);

            log.info("Leave updated for employee {}: leaveId={}, leaveType={}, startDate={}, endDate={}",
                    employeeId, leaveId, dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

            return ResponseEntity.ok(updatedLeave);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @DeleteMapping("/employees/{employeeId}/leaves/{leaveId}")
    public ResponseEntity<?> cancelLeave(
            @PathVariable Long employeeId,
            @PathVariable Long leaveId) {
        try {
            validateSelfAccess(employeeId);
            LeaveHistoryDTO cancelledLeave = leaveService.cancelLeave(employeeId, leaveId);

            log.info("Leave cancelled for employee {}: leaveId={}", employeeId, leaveId);

            return ResponseEntity.ok(cancelledLeave);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/employees/{employeeId}/leaves/{leaveId}/return")
    public ResponseEntity<?> confirmReturn(
            @PathVariable Long employeeId,
            @PathVariable Long leaveId,
            @RequestBody LeaveReturnRequest body) {
        try {
            // Authorization (owner or HR/department approver) is enforced in the
            // service, so no validateSelfAccess here — an approver may not be the
            // owning employee.
            LeaveHistoryDTO dto = leaveService.confirmReturn(
                    employeeId, leaveId, body != null ? body.getReportedDate() : null);

            log.info("Leave return confirmed for employee {}: leaveId={}, reportedDate={}",
                    employeeId, leaveId, body != null ? body.getReportedDate() : null);

            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/leaves/approvals/can-approve")
    public ResponseEntity<?> canApproveLeaves() {
        boolean canApprove = leaveService.canCurrentUserApproveLeaves();
        return ResponseEntity.ok(Map.of("canApprove", canApprove));
    }

    @GetMapping("/leaves/approvals/pending")
    public ResponseEntity<?> pendingApprovals() {
        try {
            List<LeaveHistoryDTO> approvals = leaveService.getPendingApprovalsForCurrentApprover();
            return ResponseEntity.ok(Map.of("approvals", approvals));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/leaves/approvals/history")
    public ResponseEntity<?> leaveApprovalsHistory() {
        try {
            List<LeaveHistoryDTO> approvals = leaveService.getCompanyLeaveApprovals();
            return ResponseEntity.ok(Map.of("approvals", approvals));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/leaves/{leaveId}/approve")
    public ResponseEntity<?> approveLeave(@PathVariable Long leaveId) {
        try {
            LeaveHistoryDTO history = leaveService.approveLeave(leaveId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/leaves/{leaveId}/reject")
    public ResponseEntity<?> rejectLeave(@PathVariable Long leaveId) {
        try {
            LeaveHistoryDTO history = leaveService.rejectLeave(leaveId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    private ResponseEntity<?> applyLeaveInternal(
            Long employeeId,
            LeaveRequestDTO dto,
            MultipartFile supportingDocument) {
        try {
            validateSelfAccess(employeeId);
            LeaveHistoryDTO history = leaveService.applyLeave(employeeId, dto, supportingDocument);

            log.info("Leave applied for employee {}: {} from {} to {}",
                    employeeId, dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

            return ResponseEntity.status(201).body(history);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    private void validateSelfAccess(Long employeeId) {
        User authUser = getAuthUser();

        if (authUser.getRole() == Role.ADMIN || authUser.getRole() == Role.SUPER_ADMIN) {
            return;
        }

        Employee targetEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        boolean isOwnRecord = targetEmployee.getUser() != null
                && targetEmployee.getUser().getId() != null
                && targetEmployee.getUser().getId().equals(authUser.getId());

        if (!isOwnRecord) {
            throw new AccessDeniedException("Access denied: can only access your own leave records");
        }
    }

    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unauthorized");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private ResponseEntity<Map<String, String>> forbidden(Exception e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    private ResponseEntity<Map<String, String>> badRequest(Exception e) {
        log.warn("Request failed", e);
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}