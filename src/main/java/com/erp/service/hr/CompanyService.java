package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.ChartOfAccountsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuthContext authContext;
    private final ChartOfAccountsService coaService;

    public CompanyService(CompanyRepository companyRepository, AuthContext authContext, ChartOfAccountsService coaSerivce) {
        this.companyRepository = companyRepository;
        this.authContext = authContext;
        this.coaService = coaSerivce;
    }

    // ✅ Only return companies created by the current user
    public List<Company> getAllCompanies() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }
        return companyRepository.findByCreatedBy(String.valueOf(userId));
    }

    public Company getCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
    }

    public Company createCompany(Company company) {
        Long userId = authContext.getCurrentUserId();
        if (userId != null) {
            company.setCreatedBy(String.valueOf(userId));
        }
        company.setCreatedAt(Instant.now());
        Company newCompany = companyRepository.save(company);
        coaService.createDefaultCOAForCompany(newCompany);
        return newCompany;
    }

    public Company updateCompany(Long id, Company updated) {
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
        existing.setHrEnabled(updated.isHrEnabled());
        existing.setFinanceEnabled(updated.isFinanceEnabled());
        existing.setInventoryEnabled(updated.isInventoryEnabled());
        // ✅ Preserve createdBy & createdAt
        return companyRepository.save(existing);
    }

    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }
}
