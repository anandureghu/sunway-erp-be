package com.erp.service.hr;

import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.dto.hr.BankAccountRequest;
import com.erp.dto.hr.BankAccountResponse;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CompanyRepository companyRepository;
    private final AuthContext authContext;

    @Transactional
    public BankAccountResponse create(BankAccountRequest request) {

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
        assertSameTenant(company.getId());

        if (Boolean.TRUE.equals(request.getPrimaryAccount())) {
            unsetPrimaryAccount(company.getId());
        }

        BankAccount account = BankAccount.builder()
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .branchName(request.getBranchName())
                .accountHolderName(request.getAccountHolderName())
                .primaryAccount(Boolean.TRUE.equals(request.getPrimaryAccount()))
                .company(company)
                .build();

        return map(bankAccountRepository.save(account));
    }

    public List<BankAccountResponse> getByCompany(Long companyId) {
        assertSameTenant(companyId);
        return bankAccountRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::map)
                .toList();
    }

    public BankAccountResponse getById(Long id) {
        BankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
        assertSameTenant(account);
        return map(account);
    }

    @Transactional
    public BankAccountResponse update(Long id, BankAccountRequest request) {

        BankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
        assertSameTenant(account);

        if (Boolean.TRUE.equals(request.getPrimaryAccount())) {
            unsetPrimaryAccount(account.getCompany().getId());
        }

        account.setBankName(request.getBankName());
        account.setAccountNumber(request.getAccountNumber());
        account.setIfscCode(request.getIfscCode());
        account.setBranchName(request.getBranchName());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setPrimaryAccount(Boolean.TRUE.equals(request.getPrimaryAccount()));

        return map(bankAccountRepository.save(account));
    }

    @Transactional
    public void delete(Long id) {
        BankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
        assertSameTenant(account);
        bankAccountRepository.delete(account);
    }

    private void unsetPrimaryAccount(Long companyId) {
        List<BankAccount> accounts = bankAccountRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        accounts.stream()
                .filter(BankAccount::getPrimaryAccount)
                .forEach(acc -> acc.setPrimaryAccount(false));
        bankAccountRepository.saveAll(accounts);
    }

    /* ================= TENANT GUARD ================= */

    private void assertSameTenant(BankAccount account) {
        assertSameTenant(account != null && account.getCompany() != null
                ? account.getCompany().getId()
                : null);
    }

    private void assertSameTenant(Long companyId) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId == null || companyId == null || !currentCompanyId.equals(companyId)) {
            throw new AccessDeniedException("This bank account belongs to a different company");
        }
    }

    private BankAccountResponse map(BankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
                .companyId(account.getCompany().getId())
                .bankName(account.getBankName())
                .accountNumber(account.getAccountNumber())
                .ifscCode(account.getIfscCode())
                .branchName(account.getBranchName())
                .accountHolderName(account.getAccountHolderName())
                .primaryAccount(account.getPrimaryAccount())
                .build();
    }
}
