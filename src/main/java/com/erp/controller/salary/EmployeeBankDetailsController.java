package com.erp.controller.salary;

import com.erp.dto.salary.BankDetailsRequestDTO;
import com.erp.dto.salary.BankDetailsResponseDTO;
import com.erp.service.salary.EmployeeBankDetailsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary/bank")
public class EmployeeBankDetailsController {

    private final EmployeeBankDetailsService service;

    public EmployeeBankDetailsController(EmployeeBankDetailsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<BankDetailsResponseDTO> getBankDetails(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(service.getBankDetails(employeeId));
    }

    @PostMapping
    public ResponseEntity<Void> createBank(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody BankDetailsRequestDTO dto
    ) {
        service.createBank(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> updateBank(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody BankDetailsRequestDTO dto
    ) {
        service.updateBank(employeeId, dto);
        return ResponseEntity.ok().build();
    }
}
