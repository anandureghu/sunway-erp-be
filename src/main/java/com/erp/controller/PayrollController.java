package com.erp.controller;

import com.erp.dto.payroll.PayrollRequest;
import com.erp.dto.payroll.PayrollResponse;
import com.erp.service.PayrollService;
import lombok.RequiredArgsConstructor;
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
