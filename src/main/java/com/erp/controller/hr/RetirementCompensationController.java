package com.erp.controller.hr;

import com.erp.dto.hr.RetirementCompensationDTO;
import com.erp.service.hr.RetirementCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/{employeeId}/retirement-compensation")
@RequiredArgsConstructor
public class RetirementCompensationController {

    private final RetirementCompensationService service;

    @GetMapping
    public ResponseEntity<RetirementCompensationDTO> calculate(@PathVariable Long employeeId) {
        return ResponseEntity.ok(service.calculate(employeeId));
    }
}
