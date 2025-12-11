package com.erp.service.finance;

import com.erp.domain.finance.GLAccountBalance;
import com.erp.dto.finance.GLBalanceResponseDTO;
import com.erp.repo.finance.GLAccountBalanceRepository;
import org.springframework.stereotype.Service;

@Service
public class GLAccountBalanceService {

    private final GLAccountBalanceRepository repo;

    public GLAccountBalanceService(GLAccountBalanceRepository repo) {
        this.repo = repo;
    }

    public GLBalanceResponseDTO getBalance(Long accountId, String year) {
        GLAccountBalance bal = repo.findByAccountIdAndFiscalYear(accountId, year)
                .orElse(new GLAccountBalance());

        return toDTO(bal);
    }

    public GLBalanceResponseDTO toDTO(GLAccountBalance bal) {
        return GLBalanceResponseDTO.builder()
                .accountId(bal.getAccountId())
                .fiscalYear(bal.getFiscalYear())
                .totalAssets(bal.getTotalAssets())
                .totalLiabilities(bal.getTotalLiabilities())
                .totalRevenue(bal.getTotalRevenue())
                .totalExpenses(bal.getTotalExpenses())
                .balance(bal.getBalance())
                .build();
    }
}
