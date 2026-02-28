package com.erp.controller.hr;

import com.erp.dto.hr.AccountingPeriodRequestDTO;
import com.erp.dto.hr.AccountingPeriodResponseDTO;
import com.erp.service.hr.AccountingPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting-periods")
@RequiredArgsConstructor
public class AccountingPeriodController {

    private final AccountingPeriodService periodService;

    @PostMapping
    public AccountingPeriodResponseDTO create(@RequestBody AccountingPeriodRequestDTO request) {
        return periodService.createPeriod(request);
    }

    @GetMapping
    public List<AccountingPeriodResponseDTO> getByCompany() {
        return periodService.getByCompany();
    }

    @PutMapping("/{id}/close")
    public void close(@PathVariable("id") Long id) {
        periodService.closePeriod(id);
    }

    @PutMapping("/{id}/reopen")
    public void reopen(@PathVariable("id") Long id) {
        periodService.reopenPeriod(id);
    }

    @GetMapping("/open-status")
    public AccountingPeriodResponseDTO getOpenPeriodStatus() {
        return periodService.getOpenPeriodStatus();
    }
}