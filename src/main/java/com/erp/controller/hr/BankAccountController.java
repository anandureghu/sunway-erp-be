package com.erp.controller.hr;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.BankAccountRequest;
import com.erp.dto.hr.BankAccountResponse;
import com.erp.service.hr.BankAccountService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService service;

    // HR_SETTINGS — bank accounts are company financial settings, admin-managed.
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.CREATE})
    @PostMapping
    public BankAccountResponse create(@RequestBody BankAccountRequest request) {
        return service.create(request);
    }

    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_ALL})
    @GetMapping("/{id}")
    public BankAccountResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_ALL})
    @GetMapping("/company/{companyId}")
    public List<BankAccountResponse> getByCompany(
            @PathVariable("companyId") Long companyId
    ) {
        return service.getByCompany(companyId);
    }

    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public BankAccountResponse update(
            @PathVariable("id") Long id,
            @RequestBody BankAccountRequest request
    ) {
        return service.update(id, request);
    }

    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
