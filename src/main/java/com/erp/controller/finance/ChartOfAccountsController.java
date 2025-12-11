package com.erp.controller.finance;

import com.erp.dto.finance.CreateAccountDTO;
import com.erp.dto.finance.ChartOfAccountResponseDTO;
import com.erp.service.finance.ChartOfAccountsService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/finance/chart-of-accounts")
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    public ChartOfAccountsController(ChartOfAccountsService service) {
        this.service = service;
    }

    @PostMapping
    public ChartOfAccountResponseDTO create(@RequestBody CreateAccountDTO dto) {
        return service.createAccount(dto);
    }

    @GetMapping("/company/{companyId}")
    public List<ChartOfAccountResponseDTO> listAll(@PathVariable("companyId") Long companyId) {
        return service.listAll(companyId);
    }

    @GetMapping("/{id}")
    public ChartOfAccountResponseDTO getById(@PathVariable("id") Long id) {
        return service.getById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
