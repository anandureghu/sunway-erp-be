package com.erp.controller.finance;

import com.erp.domain.finance.GLAccountBalance;
import com.erp.service.finance.ChartOfAccountsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/gl")
public class GLAccountBalanceController {

    private final ChartOfAccountsService chartOfAccountsService;

    public GLAccountBalanceController(ChartOfAccountsService chartOfAccountsService) {
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @GetMapping("/balance/{accountId}/{year}")
    public GLAccountBalance getBalance(@PathVariable("accountId") Long accountId, @PathVariable("year") String year) {
        return chartOfAccountsService.getGlBalanceForAccount(accountId, year);
    }
}
