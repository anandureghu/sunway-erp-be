package com.erp.controller.finance;

import com.erp.domain.finance.GLAccountBalance;
import com.erp.repo.finance.GLAccountBalanceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/finance/gl")
public class GLAccountBalanceController {

    private final GLAccountBalanceRepository balRepo;
    public GLAccountBalanceController(GLAccountBalanceRepository balRepo) { this.balRepo = balRepo; }

    @GetMapping("/balance/{accountId}/{year}")
    public GLAccountBalance getBalance(@PathVariable("accountId") Long accountId, @PathVariable("year") String year) {
        return balRepo.findByAccountIdAndFiscalYear(accountId, year).orElse(null);
    }
}
