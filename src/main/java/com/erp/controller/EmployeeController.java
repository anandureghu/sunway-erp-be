package com.erp.controller;

import com.erp.dto.common.PageResponse;
import com.erp.dto.employee.EmployeeRequest;
import com.erp.dto.employee.EmployeeResponse;
import com.erp.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    // Create
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeResponse> create(@RequestBody @Valid EmployeeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // Update
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @PutMapping(path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeResponse> update(@PathVariable("id") Long id,
                                                   @RequestBody @Valid EmployeeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    // Delete
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Get by id
    @PreAuthorize("hasAnyRole('ADMIN','HR','USER')")
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    // List (paged)
    // Explicit @RequestParam names avoid the need for the compiler -parameters flag
    @PreAuthorize("hasAnyRole('ADMIN','HR','USER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageResponse<EmployeeResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        // (Optional) guardrails to avoid huge pages
        if (size < 1) size = 1;
        if (size > 200) size = 200;

        return ResponseEntity.ok(service.list(page, size));
    }
}
