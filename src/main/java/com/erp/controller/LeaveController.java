// src/main/java/com/hrmodule/web/LeaveController.java
package com.erp.web;

import com.erp.dto.leave.LeaveRequest;
import com.erp.dto.leave.LeaveResponse;
import com.erp.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService service;

    @GetMapping("/leaves/employee/{employeeId}")
    public List<LeaveResponse> listForEmployee(@PathVariable("employeeId") Long employeeId) {
        return service.listForEmployee(employeeId);
    }

    @PostMapping("/leaves")
    public LeaveResponse create(@RequestBody LeaveRequest req) {
        return service.create(req);
    }

    @PutMapping("/leaves/{id}")
    public LeaveResponse update(@PathVariable("id") Long id, @RequestBody LeaveRequest req) {
        return service.update(id, req);
    }
}
