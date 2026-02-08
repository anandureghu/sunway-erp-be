package com.erp.controller.hr;

import com.erp.dto.hr.BankAccountRequest;
import com.erp.dto.hr.BankAccountResponse;
import com.erp.service.hr.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;

    @PostMapping
    public BankAccountResponse create(@RequestBody BankAccountRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public BankAccountResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/company/{companyId}")
    public List<BankAccountResponse> getByCompany(
            @PathVariable("companyId") Long companyId
    ) {
        return service.getByCompany(companyId);
    }

    @PutMapping("/{id}")
    public BankAccountResponse update(
            @PathVariable("id") Long id,
            @RequestBody BankAccountRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}

