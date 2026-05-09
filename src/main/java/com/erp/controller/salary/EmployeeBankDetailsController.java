package com.erp.controller.salary;

import com.erp.domain.salary.AccountType;
import com.erp.dto.salary.BankDetailsRequestDTO;
import com.erp.dto.salary.BankDetailsResponseDTO;
import com.erp.service.salary.EmployeeBankDetailsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

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

    @GetMapping("/account-types")
    public ResponseEntity<List<AccountTypeOption>> getAccountTypes() {
        List<AccountTypeOption> types = Arrays.stream(AccountType.values())
                .map(type -> new AccountTypeOption(type.name(), type.getDisplayName()))
                .toList();
        return ResponseEntity.ok(types);
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

    @lombok.Data
    @lombok.AllArgsConstructor
    static class AccountTypeOption {
        private String value;
        private String label;
    }
}
