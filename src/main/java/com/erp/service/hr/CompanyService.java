package com.erp.service.hr;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyInvoiceSettings;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.hr.Currency;
import com.erp.dto.hr.AccountingDefaultsDTO;
import com.erp.dto.hr.CompanyDTO;
import com.erp.dto.hr.InvoiceBrandingSettingsDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyInvoiceSettingsRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.hr.CurrencyRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CompanyService {
    private final CompanyRepository           companyRepository;
    private final CompanyInvoiceSettingsRepository invoiceSettingsRepository;
    private final CompanyRoleRepository       roleRepository;
    private final CurrencyRepository          currencyRepository;
    private final ChartOfAccountsRepository   chartOfAccountsRepository;
    private final BankAccountRepository       bankAccountRepository;
    private final AuthContext                 authContext;

    public CompanyService(
            CompanyRepository companyRepository,
            CompanyInvoiceSettingsRepository invoiceSettingsRepository,
            CompanyRoleRepository roleRepository,
            CurrencyRepository currencyRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            BankAccountRepository bankAccountRepository,
            AuthContext authContext) {

        this.companyRepository         = companyRepository;
        this.invoiceSettingsRepository = invoiceSettingsRepository;
        this.roleRepository            = roleRepository;
        this.currencyRepository        = currencyRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.bankAccountRepository     = bankAccountRepository;
        this.authContext               = authContext;
    }

    // ======================================================
    // GET ALL COMPANIES
    // ======================================================
    public List<Company> getAllCompanies() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("User not authenticated");
        return companyRepository.findAll().stream()
                .map(this::hydrateInvoiceBrandingView)
                .toList();
    }

    // ======================================================
    // GET COMPANY BY ID
    // ======================================================
    public Company getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return hydrateInvoiceBrandingView(company);
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
                .companyEmail(defaultIfBlank(dto.getCompanyEmail(), "info@company.com"))
                .billingEmail(defaultIfBlank(dto.getBillingEmail(), "accounts@company.com"))
                .websiteUrl(defaultIfBlank(dto.getWebsiteUrl(), "https://example.com"))
                .taxRate(dto.getTaxRate() != null ? String.valueOf(dto.getTaxRate()) : null)
                .isTaxActive(dto.isTaxActive())
                .hrEnabled(dto.isHrEnabled())
                .financeEnabled(dto.isFinanceEnabled())
                .inventoryEnabled(dto.isInventoryEnabled())
                .createdAt(Instant.now())
                .build();

        Long userId = authContext.getCurrentUserId();
        if (userId != null) company.setCreatedBy(String.valueOf(userId));

        Company saved = companyRepository.save(company);
        invoiceSettingsRepository.save(InvoiceSettingsDefaults.buildDefaults(saved));

        // Seed default roles for this company on day 1
        // HR can add/rename/delete these later from Settings → Roles
        seedDefaultRoles(saved);

        return hydrateInvoiceBrandingView(saved);
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
        existing.setCompanyEmail(trimToNull(updated.getCompanyEmail()));
        existing.setBillingEmail(trimToNull(updated.getBillingEmail()));
        existing.setWebsiteUrl(trimToNull(updated.getWebsiteUrl()));
        existing.setTaxRate(updated.getTaxRate() != null ? String.valueOf(updated.getTaxRate()) : null);
        existing.setTaxActive(updated.isTaxActive());
        existing.setCurrency(currency);
        existing.setHrEnabled(updated.isHrEnabled());
        existing.setFinanceEnabled(updated.isFinanceEnabled());
        existing.setInventoryEnabled(updated.isInventoryEnabled());

        Company saved = companyRepository.save(existing);
        return hydrateInvoiceBrandingView(saved);
    }

    @Transactional
    public Company updateAccountingDefaults(Long companyId, AccountingDefaultsDTO dto) {
        Long currentCompanyId = authContext.getCurrentCompanyId();
        if (currentCompanyId == null || !currentCompanyId.equals(companyId)) {
            throw new RuntimeException("Not allowed to update this company's accounting defaults");
        }
        Company company = getCompanyById(companyId);
        applyAccountingDefaults(company, dto);
        return companyRepository.save(company);
    }

    @Transactional
    public Company updateInvoiceBrandingSettings(Long companyId, InvoiceBrandingSettingsDTO dto) {
        String role = authContext.getCurrentUserRole();
        if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new RuntimeException("Only SUPER_ADMIN can update invoice branding settings");
        }
        Company company = getCompanyById(companyId);
        CompanyInvoiceSettings settings = getOrCreateInvoiceSettings(company);
        applyInvoiceBrandingSettings(settings, dto);
        invoiceSettingsRepository.save(settings);
        return hydrateInvoiceBrandingView(company);
    }

    private void applyAccountingDefaults(Company company, AccountingDefaultsDTO dto) {
        Long cid = company.getId();
        company.setDefaultSalesDebitAccountId(
                resolveCoaId(cid, dto.getDefaultSalesDebitAccountId(), "Default sales debit account"));
        company.setDefaultSalesCreditAccountId(
                resolveCoaId(cid, dto.getDefaultSalesCreditAccountId(), "Default sales credit account"));
        company.setDefaultPurchaseDebitAccountId(
                resolveCoaId(cid, dto.getDefaultPurchaseDebitAccountId(), "Default purchase debit account"));
        company.setDefaultPurchaseCreditAccountId(
                resolveCoaId(cid, dto.getDefaultPurchaseCreditAccountId(), "Default purchase credit account"));
        company.setDefaultBankAccountId(
                resolveBankId(cid, dto.getDefaultBankAccountId()));
    }

    private void applyInvoiceBrandingSettings(CompanyInvoiceSettings settings, InvoiceBrandingSettingsDTO dto) {
        settings.setInvoiceHeaderSubtitle(trimToNull(dto.getInvoiceHeaderSubtitle()));
        settings.setInvoiceNotesUnpaid(trimToNull(dto.getInvoiceNotesUnpaid()));
        settings.setInvoiceNotesPaid(trimToNull(dto.getInvoiceNotesPaid()));
        settings.setInvoiceTerms(trimToNull(dto.getInvoiceTerms()));
        settings.setInvoiceFooterCompanyLine(trimToNull(dto.getInvoiceFooterCompanyLine()));
        settings.setInvoiceFooterTaxLine(trimToNull(dto.getInvoiceFooterTaxLine()));
        settings.setInvoiceFooterSignatureNote(trimToNull(dto.getInvoiceFooterSignatureNote()));
        settings.setInvoiceFooterSupportEmail(trimToNull(dto.getInvoiceFooterSupportEmail()));
        settings.setInvoiceFooterBillingEmail(trimToNull(dto.getInvoiceFooterBillingEmail()));
        settings.setInvoiceQrEnabled(Boolean.TRUE.equals(dto.getInvoiceQrEnabled()));
    }

    private CompanyInvoiceSettings getOrCreateInvoiceSettings(Company company) {
        return invoiceSettingsRepository.findByCompanyId(company.getId())
                .orElseGet(() -> invoiceSettingsRepository.save(InvoiceSettingsDefaults.buildDefaults(company)));
    }

    private Company hydrateInvoiceBrandingView(Company company) {
        CompanyInvoiceSettings settings = getOrCreateInvoiceSettings(company);
        company.setInvoiceHeaderSubtitle(settings.getInvoiceHeaderSubtitle());
        company.setInvoiceNotesUnpaid(settings.getInvoiceNotesUnpaid());
        company.setInvoiceNotesPaid(settings.getInvoiceNotesPaid());
        company.setInvoiceTerms(settings.getInvoiceTerms());
        company.setInvoiceFooterCompanyLine(settings.getInvoiceFooterCompanyLine());
        company.setInvoiceFooterTaxLine(settings.getInvoiceFooterTaxLine());
        company.setInvoiceFooterSignatureNote(settings.getInvoiceFooterSignatureNote());
        company.setInvoiceFooterSupportEmail(settings.getInvoiceFooterSupportEmail());
        company.setInvoiceFooterBillingEmail(settings.getInvoiceFooterBillingEmail());
        company.setInvoiceQrEnabled(settings.isInvoiceQrEnabled());
        return company;
    }

    private Long resolveCoaId(Long companyId, Long coaId, String label) {
        if (coaId == null) {
            return null;
        }
        ChartOfAccounts coa = chartOfAccountsRepository.findById(coaId)
                .orElseThrow(() -> new RuntimeException(label + " not found"));
        if (!coa.getCompany().getId().equals(companyId)) {
            throw new RuntimeException(label + " does not belong to this company");
        }
        return coaId;
    }

    private Long resolveBankId(Long companyId, Long bankId) {
        if (bankId == null) {
            return null;
        }
        BankAccount bank = bankAccountRepository.findById(bankId)
                .orElseThrow(() -> new RuntimeException("Default bank account not found"));
        if (!bank.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Default bank account does not belong to this company");
        }
        return bankId;
    }

    // ======================================================
    // DELETE COMPANY
    // ======================================================
    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }

    // ======================================================
    // SEED DEFAULT ROLES
    // Called once on company creation — not exposed via API
    // ======================================================
    private void seedDefaultRoles(Company company) {

        // { name, description }
        List<String[]> defaults = List.of(
                new String[]{ "Admin",            "Full system access"               },
                new String[]{ "HR Manager",       "HR and payroll management"        },
                new String[]{ "Finance Manager",  "Finance and budget management"    },
                new String[]{ "Accountant",       "Accounting and reporting"         },
                new String[]{ "AP/AR Clerk",      "Accounts payable and receivable"  },
                new String[]{ "Controller",       "Financial control"                },
                new String[]{ "External Auditor", "External audit read access"       },
                new String[]{ "Employee",         "Standard employee access"         }
        );

        List<CompanyRole> roles = defaults.stream()
                .filter(d -> !roleRepository.existsByCompanyIdAndName(company.getId(), d[0]))
                .map(d -> CompanyRole.builder()
                        .name(d[0])
                        .description(d[1])
                        .active(true)
                        .company(company)
                        .build())
                .toList();

        roleRepository.saveAll(roles);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}