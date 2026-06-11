package com.erp.controller.finance;

import com.erp.domain.finance.GLAccountBalance;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.service.finance.ChartOfAccountsService;
import com.erp.service.security.annotation.RequiresPermission;
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

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/balance/{accountId}/{year}")
    public GLAccountBalance getBalance(@PathVariable("accountId") Long accountId, @PathVariable("year") String year) {
        return chartOfAccountsService.getGlBalanceForAccount(accountId, year);
    }
}
