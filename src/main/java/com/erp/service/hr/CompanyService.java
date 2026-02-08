package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Currency;
import com.erp.dto.hr.CompanyDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CurrencyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.LeavePolicyService;
import com.erp.service.finance.ChartOfAccountsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuthContext authContext;
    private final ChartOfAccountsService coaService;
    private final LeavePolicyService leavePolicyService;
    private final CurrencyRepository currencyRepository;

    public CompanyService(
            CompanyRepository companyRepository,
            AuthContext authContext,
            ChartOfAccountsService coaService,
            LeavePolicyService leavePolicyService,
            CurrencyRepository currencyRepository) {
        this.companyRepository = companyRepository;
        this.authContext = authContext;
        this.coaService = coaService;
        this.leavePolicyService = leavePolicyService;
        this.currencyRepository = currencyRepository;
    }

    // ✅ Only return companies created by the current user
    public List<Company> getAllCompanies() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }
        return companyRepository.findAll();
    }

    public Company getCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
    }

    @Transactional
    public Company createCompany(CompanyDTO dto) {
        Currency currency = currencyRepository.findById(dto.getCurrencyId()).orElseThrow(() -> new RuntimeException("Currency not found"));
        Company company = Company.builder()
                .companyName(dto.getCompanyName())
                .noOfEmployees(dto.getNoOfEmployees())
                .currency(currency)
                .crNo(dto.getCrNo())
                .computerCard(dto.getComputerCard())
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .phoneNo(dto.getPhoneNo())
                .hrEnabled(dto.isHrEnabled())
                .financeEnabled(dto.isFinanceEnabled())
                .inventoryEnabled(dto.isInventoryEnabled())
                .build();

        Long userId = authContext.getCurrentUserId();
        if (userId != null) {
            company.setCreatedBy(String.valueOf(userId));
        }
        company.setCreatedAt(Instant.now());

        // Save company first
        Company newCompany = companyRepository.save(company);

        // Create default Chart of Accounts
        coaService.createDefaultCOAForCompany(newCompany);

        // Auto-initialize default leave policies if HR is enabled
        if (newCompany.isHrEnabled()) {
            try {
                leavePolicyService.initializeDefaultPoliciesForCompany(newCompany.getId());
            } catch (Exception e) {
                // Log but don't fail company creation if policies fail
                System.err.println("Could not initialize leave policies for company "
                        + newCompany.getId() + ": " + e.getMessage());
            }
        }

        return newCompany;
    }

    public Company updateCompany(Long id, CompanyDTO updated) {

        Currency currency = currencyRepository.findById(updated.getCurrencyId()).orElseThrow(() -> new RuntimeException("Currency not found"));

        Company existing = getCompanyById(id);
        existing.setCompanyName(updated.getCompanyName());
        existing.setNoOfEmployees(updated.getNoOfEmployees());
        existing.setCrNo(updated.getCrNo());
        existing.setComputerCard(updated.getComputerCard());
        existing.setStreet(updated.getStreet());
        existing.setCity(updated.getCity());
        existing.setState(updated.getState());
        existing.setCountry(updated.getCountry());
        existing.setPhoneNo(updated.getPhoneNo());
        existing.setCurrency(currency);

        // If HR is being enabled for the first time, initialize leave policies
        boolean wasHrDisabled = !existing.isHrEnabled();
        boolean isNowHrEnabled = updated.isHrEnabled();

        existing.setHrEnabled(updated.isHrEnabled());
        existing.setFinanceEnabled(updated.isFinanceEnabled());
        existing.setInventoryEnabled(updated.isInventoryEnabled());

        Company savedCompany = companyRepository.save(existing);

        // Initialize leave policies if HR was just enabled
        if (wasHrDisabled && isNowHrEnabled) {
            try {
                leavePolicyService.initializeDefaultPoliciesForCompany(savedCompany.getId());
            } catch (Exception e) {
                System.err.println("Could not initialize leave policies: " + e.getMessage());
            }
        }

        return savedCompany;
    }

    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }
}