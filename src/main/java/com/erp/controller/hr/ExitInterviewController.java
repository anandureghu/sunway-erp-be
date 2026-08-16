package com.erp.controller.hr;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.ExitInterviewDTO;
import com.erp.service.hr.ExitInterviewService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exit interview for a departing employee. Nested under the employee resource; the
 * service enforces the tenant boundary and that the employee is in an exit status.
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/exit-interview")
public class ExitInterviewController {

    private final ExitInterviewService service;

    public ExitInterviewController(ExitInterviewService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<ExitInterviewDTO> get(@PathVariable("employeeId") Long employeeId) {
        return ResponseEntity.ok(service.get(employeeId));
    }

    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
    @PutMapping
    public ResponseEntity<ExitInterviewDTO> save(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody ExitInterviewDTO dto) {
        return ResponseEntity.ok(service.save(employeeId, dto));
    }
}
