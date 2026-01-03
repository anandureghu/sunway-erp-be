package com.erp.controller.salary;

import com.erp.dto.salary.CompensationRequestDTO;
import com.erp.dto.salary.SalaryResponseDTO;
import com.erp.service.salary.EmployeeCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary")
@RequiredArgsConstructor
public class EmployeeCompensationController {

    private final EmployeeCompensationService service;

    /* ================= GET ACTIVE SALARY ================= */

    @GetMapping
    public ResponseEntity<SalaryResponseDTO> getSalary(
            @PathVariable("employeeId") Long employeeId) {

        SalaryResponseDTO salary =
                service.getActiveCompensation(employeeId);

        if (salary == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(salary);
    }

    /* ================= CREATE SALARY ================= */

    @PostMapping
    public ResponseEntity<Void> createSalary(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody CompensationRequestDTO dto) {

        service.createSalary(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    /* ================= UPDATE SALARY ================= */

    @PutMapping
    public ResponseEntity<Void> updateSalary(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody CompensationRequestDTO dto) {

        service.updateSalary(employeeId, dto);
        return ResponseEntity.ok().build();
    }
}
