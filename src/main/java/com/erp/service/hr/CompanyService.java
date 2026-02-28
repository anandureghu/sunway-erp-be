package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Currency;
import com.erp.dto.hr.CompanyDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CurrencyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuthContext authContext;
    private final CurrencyRepository currencyRepository;

    public CompanyService(
            CompanyRepository companyRepository,
            AuthContext authContext,
            CurrencyRepository currencyRepository) {

        this.companyRepository = companyRepository;
        this.authContext = authContext;
        this.currencyRepository = currencyRepository;
    }

    // ======================================================
    // GET ALL COMPANIES
    // ======================================================
    public List<Company> getAllCompanies() {

        Long userId = authContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }

        return companyRepository.findAll();
    }

    // ======================================================
    // GET COMPANY BY ID
    // ======================================================
    public Company getCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
    }

    // ======================================================
    // CREATE COMPANY
    // ======================================================
    @Transactional
    public Company createCompany(CompanyDTO dto) {

        Currency currency = currencyRepository.findById(dto.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));

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
                .createdAt(Instant.now())
                .build();

        Long userId = authContext.getCurrentUserId();
        if (userId != null) {
            company.setCreatedBy(String.valueOf(userId));
        }

        return companyRepository.save(company);
    }

    // ======================================================
    // UPDATE COMPANY
    // ======================================================
    @Transactional
    public Company updateCompany(Long id, CompanyDTO updated) {

        Company existing = getCompanyById(id);

        Currency currency = currencyRepository.findById(updated.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));

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

        existing.setHrEnabled(updated.isHrEnabled());
        existing.setFinanceEnabled(updated.isFinanceEnabled());
        existing.setInventoryEnabled(updated.isInventoryEnabled());

        return companyRepository.save(existing);
    }

    // ======================================================
    // DELETE COMPANY
    // ======================================================
    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }
}
