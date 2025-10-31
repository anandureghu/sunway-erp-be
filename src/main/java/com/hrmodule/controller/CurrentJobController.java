// src/main/java/com/hrmodule/controller/CurrentJobController.java
package com.hrmodule.controller;

import com.hrmodule.dto.currentjob.CurrentJobRequest;
import com.hrmodule.dto.currentjob.CurrentJobResponse;
import com.hrmodule.service.CurrentJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{empId}/current-job")
public class CurrentJobController {

    private final CurrentJobService service;
    public CurrentJobController(CurrentJobService service) { this.service = service; }

    // Anyone who can view employee can view current job
    @PreAuthorize("hasAnyRole('ADMIN','HR','USER')")
    @GetMapping
    public ResponseEntity<CurrentJobResponse> get(@PathVariable("empId") Long empId) {
        return ResponseEntity.ok(service.getForEmployee(empId));
    }

    // Update any part (PATCH semantics) — FE currently uses PUT, but both behave same here
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PutMapping
    public ResponseEntity<CurrentJobResponse> put(
            @PathVariable("empId") Long empId,
            @RequestBody CurrentJobRequest req
    ) {
        return ResponseEntity.ok(service.upsertForEmployee(empId, req));
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PatchMapping
    public ResponseEntity<CurrentJobResponse> patch(
            @PathVariable("empId") Long empId,
            @RequestBody CurrentJobRequest req
    ) {
        return ResponseEntity.ok(service.upsertForEmployee(empId, req));
    }
}
