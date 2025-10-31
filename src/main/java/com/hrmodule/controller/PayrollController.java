package com.hrmodule.controller;

import com.hrmodule.dto.payroll.PayrollRequest;
import com.hrmodule.dto.payroll.PayrollResponse;
import com.hrmodule.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService service;

    @GetMapping("/payrolls/employee/{employeeId}")
    public List<PayrollResponse> listForEmployee(@PathVariable("employeeId") Long employeeId) {
        return service.listForEmployee(employeeId);
    }

    @GetMapping("/payrolls/{id}")
    public PayrollResponse get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping("/payrolls")
    public PayrollResponse create(@RequestBody PayrollRequest req) {
        return service.create(req);
    }

    @PutMapping("/payrolls/{id}")
    public PayrollResponse update(@PathVariable("id") Long id,
                                  @RequestBody PayrollRequest req) {
        return service.update(id, req);
    }
}
