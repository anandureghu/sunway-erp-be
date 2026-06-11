package com.erp.controller.finance;

import com.erp.dto.finance.ChartOfAccountResponseDTO;
import com.erp.dto.finance.CreateAccountDTO;
import com.erp.dto.finance.SetInitialBalanceDTO;
import com.erp.dto.finance.UpdateAccountDTO;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.service.finance.ChartOfAccountsService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/chart-of-accounts")
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    public ChartOfAccountsController(ChartOfAccountsService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.CREATE})
    @PostMapping
    public ChartOfAccountResponseDTO create(@RequestBody CreateAccountDTO dto) {
        return service.createAccount(dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping()
    public List<ChartOfAccountResponseDTO> listAll() {
        return service.listAll();
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ChartOfAccountResponseDTO getById(@PathVariable("id") Long id) {
        return service.getById(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ChartOfAccountResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody UpdateAccountDTO dto) {
        return service.updateAccount(id, dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.EDIT})
    @PatchMapping("/{id}/initial-balance")
    public ChartOfAccountResponseDTO setInitialBalance(
            @PathVariable("id") Long id,
            @RequestBody SetInitialBalanceDTO dto) {
        return service.setInitialBalance(id, dto.getAmount());
    }

    @RequiresPermission(module = AppModule.FINANCE_COA, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
