package com.erp.service.hr;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.hr.BankAccount;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyInvoiceSettings;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.hr.Currency;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.hr.AccountingDefaultsDTO;
import com.erp.dto.hr.CompanyDTO;
import com.erp.dto.hr.HrPoliciesDTO;
import com.erp.dto.hr.InvoiceBrandingSettingsDTO;
import com.erp.dto.hr.PayrollExportSettingsDTO;
import com.erp.dto.hr.StorageUsageDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.hr.BankAccountRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyInvoiceSettingsRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.hr.CurrencyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.service.file.FileStorageService;
import com.erp.service.salary.EmployeeCompensationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class CompanyService {
    private final CompanyRepository           companyRepository;
    private final CompanyInvoiceSettingsRepository invoiceSettingsRepository;
    private final CompanyRoleRepository       companyRoleRepository;
    private final CurrencyRepository          currencyRepository;
    private final ChartOfAccountsRepository   chartOfAccountsRepository;
    private final BankAccountRepository       bankAccountRepository;
    private final AuthContext                 authContext;
    private final FileStorageService          fileStorageService;
    private final DocumentSequenceService     documentSequenceService;
    private final EmployeeRepository          employeeRepository;
    private final CompanyStorageService       companyStorageService;
    private final QatarLaborLawDefaultsService qatarLaborLawDefaultsService;
    private final EmployeeCompensationService employeeCompensationService;

    public CompanyService(
            CompanyRepository companyRepository,
            CompanyInvoiceSettingsRepository invoiceSettingsRepository,
            CompanyRoleRepository companyRoleRepository,
            CurrencyRepository currencyRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            BankAccountRepository bankAccountRepository,
            AuthContext authContext,
            FileStorageService fileStorageService,
            DocumentSequenceService documentSequenceService,
            EmployeeRepository employeeRepository,
            CompanyStorageService companyStorageService,
            @Lazy QatarLaborLawDefaultsService qatarLaborLawDefaultsService,
            @Lazy EmployeeCompensationService employeeCompensationService) {

        this.companyRepository         = companyRepository;
        this.invoiceSettingsRepository = invoiceSettingsRepository;
        this.companyRoleRepository     = companyRoleRepository;
        this.currencyRepository        = currencyRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.bankAccountRepository     = bankAccountRepository;
        this.authContext               = authContext;
        this.fileStorageService        = fileStorageService;
        this.documentSequenceService   = documentSequenceService;
        this.employeeRepository        = employeeRepository;
        this.companyStorageService     = companyStorageService;
        this.qatarLaborLawDefaultsService = qatarLaborLawDefaultsService;
        this.employeeCompensationService = employeeCompensationService;
    }

    // ======================================================
    // GET ALL COMPANIES
    // ======================================================
    public List<Company> getAllCompanies() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("User not authenticated");
        if (isSuperAdmin()) {
            return companyRepository.findAll().stream()
                    .map(this::hydrateInvoiceBrandingView)
                    .map(this::hydrateStorageUsage)
                    .toList();
        }
        Long cid = authContext.getCurrentCompanyId();
        if (cid == null) {
            return Collections.emptyList();
        }
        return companyRepository.findById(cid)
                .map(c -> List.of(hydrateInvoiceBrandingView(c)))
                .orElse(Collections.emptyList());
    }

    // ======================================================
    // GET COMPANY BY ID
    // ======================================================
    public Company getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        boolean isSuperAdmin = isSuperAdmin();
        if (!isSuperAdmin) {
            Long current = authContext.getCurrentCompanyId();
            if (current == null || !current.equals(id)) {
                throw new AccessDeniedException("Access denied for this company");
            }
        }
        company = hydrateInvoiceBrandingView(company);
        return isSuperAdmin ? hydrateStorageUsage(company) : company;
    }

    private boolean isSuperAdmin() {
        String role = authContext.getCurrentUserRole();
        return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    // ======================================================
    // CREATE COMPANY
    // ======================================================
    @Transactional
    public Company createCompany(CompanyDTO dto, MultipartFile logo) {

        Currency currency = currencyRepository.findById(dto.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));

        Company company = Company.builder()
                .companyName(dto.getCompanyName())
                .noOfEmployees(dto.getNoOfEmployees())
                .industry(dto.getIndustry())
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
                .annualLeaveAccrualEnabled(Boolean.TRUE.equals(dto.getAnnualLeaveAccrualEnabled()))
                .annualLeaveAccrualDaysPerMonth(
                        dto.getAnnualLeaveAccrualDaysPerMonth() != null
                                ? dto.getAnnualLeaveAccrualDaysPerMonth()
                                : new BigDecimal("1.50"))
                .minServiceMonthsForAnnualLeave(
                        dto.getMinServiceMonthsForAnnualLeave() != null
                                ? dto.getMinServiceMonthsForAnnualLeave()
                                : 6)
                .retirementCompensationEnabled(Boolean.TRUE.equals(dto.getRetirementCompensationEnabled()))
                .retirementCompensationMonthsPerYear(
                        dto.getRetirementCompensationMonthsPerYear() != null
                                ? dto.getRetirementCompensationMonthsPerYear()
                                : new BigDecimal("1.00"))
                .createdAt(Instant.now())
                .build();

        Long userId = authContext.getCurrentUserId();
        if (userId != null) company.setCreatedBy(String.valueOf(userId));

        Company saved = companyRepository.save(company);
        seedDefaultCompanyRoles(saved);
        qatarLaborLawDefaultsService.applyToCompany(saved.getId());
        invoiceSettingsRepository.save(InvoiceSettingsDefaults.buildDefaults(saved));
        // Start this company's employee-number sequence at 1000 up front.
        documentSequenceService.initEmployeeSequence(saved.getId());

        if (logo != null && !logo.isEmpty()) {
            FileUploadResult upload = fileStorageService.upload(
                    logo,
                    FileCategory.COMPANY_LOGO,
                    saved.getId().toString(),
                    true,
                    saved.getId()
            );
            saved.setLogoUrl(fileStorageService.buildPublicUrl(upload.getBlobPath()));
            saved = companyRepository.save(saved);
        }

        return hydrateInvoiceBrandingView(saved);
    }

    // ======================================================
    // UPDATE COMPANY
    // ======================================================
    @Transactional
    public Company updateCompany(Long id, CompanyDTO updated, MultipartFile logo) {

        Company existing = getCompanyById(id);

        Currency currency = currencyRepository.findById(updated.getCurrencyId())
                .orElseThrow(() -> new RuntimeException("Currency not found"));

        existing.setCompanyName(updated.getCompanyName());
        existing.setCompanyCode(updated.getCompanyCode());
        existing.setNoOfEmployees(updated.getNoOfEmployees());
        existing.setIndustry(updated.getIndustry());
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

        // Leave accrual policy (only overwrite when client explicitly sends a value
        // so that partial updates don't silently disable accrual).
        if (updated.getAnnualLeaveAccrualEnabled() != null) {
            existing.setAnnualLeaveAccrualEnabled(updated.getAnnualLeaveAccrualEnabled());
        }
        if (updated.getAnnualLeaveAccrualDaysPerMonth() != null) {
            existing.setAnnualLeaveAccrualDaysPerMonth(updated.getAnnualLeaveAccrualDaysPerMonth());
        }
        if (updated.getMinServiceMonthsForAnnualLeave() != null) {
            existing.setMinServiceMonthsForAnnualLeave(updated.getMinServiceMonthsForAnnualLeave());
        }

        // Retirement compensation policy
        if (updated.getRetirementCompensationEnabled() != null) {
            existing.setRetirementCompensationEnabled(updated.getRetirementCompensationEnabled());
        }
        if (updated.getRetirementCompensationMonthsPerYear() != null) {
            existing.setRetirementCompensationMonthsPerYear(updated.getRetirementCompensationMonthsPerYear());
        }

        // Preserve existing logo when no file uploaded; replace when a new file is provided.
        if (logo != null && !logo.isEmpty()) {
            FileUploadResult upload = fileStorageService.upload(
                    logo,
                    FileCategory.COMPANY_LOGO,
                    existing.getId().toString(),
                    true,
                    existing.getId()
            );
            existing.setLogoUrl(fileStorageService.buildPublicUrl(upload.getBlobPath()));
        }

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
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new RuntimeException("Only ADMIN or SUPER_ADMIN can update invoice branding settings");
        }
        Company company = getCompanyById(companyId);
        CompanyInvoiceSettings settings = getOrCreateInvoiceSettings(company);
        applyInvoiceBrandingSettings(settings, dto);
        invoiceSettingsRepository.save(settings);
        return hydrateInvoiceBrandingView(company);
    }

    private void applyAccountingDefaults(Company company, AccountingDefaultsDTO dto) {
        Long cid = company.getId();
        if (dto.getDefaultSalesDebitAccountId() != null) {
            company.setDefaultSalesDebitAccountId(
                    resolveCoaId(cid, dto.getDefaultSalesDebitAccountId(), "Default sales debit account"));
        }
        if (dto.getDefaultSalesCreditAccountId() != null) {
            company.setDefaultSalesCreditAccountId(
                    resolveCoaId(cid, dto.getDefaultSalesCreditAccountId(), "Default sales credit account"));
        }
        if (dto.getDefaultPurchaseDebitAccountId() != null) {
            company.setDefaultPurchaseDebitAccountId(
                    resolveCoaId(cid, dto.getDefaultPurchaseDebitAccountId(), "Default purchase debit account"));
        }
        if (dto.getDefaultPurchaseCreditAccountId() != null) {
            company.setDefaultPurchaseCreditAccountId(
                    resolveCoaId(cid, dto.getDefaultPurchaseCreditAccountId(), "Default purchase credit account"));
        }
        if (dto.getDefaultBankAccountId() != null) {
            company.setDefaultBankAccountId(resolveBankId(cid, dto.getDefaultBankAccountId()));
        }
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
        company.setEmployeeCount(
                employeeRepository.countByCompany_IdAndStatus(company.getId(), EmployeeStatus.ACTIVE));
        return company;
    }

    private Company hydrateStorageUsage(Company company) {
        StorageUsageDTO usage = companyStorageService.getStorageUsage(company.getId());
        company.setCloudStorageBytes(usage.getCloudStorageBytes());
        company.setDatabaseStorageBytes(usage.getDatabaseStorageBytes());
        company.setStorageCalculatedAt(usage.getDatabaseStorageCalculatedAt());
        return company;
    }

    // ======================================================
    // STORAGE USAGE (manual recalculation — SUPER_ADMIN only)
    // ======================================================
    public Company recalculateStorageUsage(Long companyId) {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("Only SUPER_ADMIN can recalculate storage usage");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        companyStorageService.recalculateDatabaseStorage(companyId);
        return hydrateStorageUsage(hydrateInvoiceBrandingView(company));
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
    // HR POLICIES (leave accrual + retirement compensation)
    // ======================================================
    public HrPoliciesDTO getHrPolicies(Long companyId) {
        Company company = getCompanyById(companyId);
        return toHrPoliciesDto(company);
    }

    @Transactional
    public HrPoliciesDTO updateHrPolicies(Long companyId, HrPoliciesDTO dto) {
        Long currentCompanyId = authContext.getCurrentCompanyId();
        String role = authContext.getCurrentUserRole();
        boolean isSuperAdmin = role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
        if (!isSuperAdmin && (currentCompanyId == null || !currentCompanyId.equals(companyId))) {
            throw new RuntimeException("Not allowed to update this company's HR policies");
        }

        Company company = getCompanyById(companyId);

        if (dto.getAnnualLeaveAccrualEnabled() != null) {
            company.setAnnualLeaveAccrualEnabled(dto.getAnnualLeaveAccrualEnabled());
        }
        if (dto.getAnnualLeaveAccrualDaysPerMonth() != null) {
            company.setAnnualLeaveAccrualDaysPerMonth(dto.getAnnualLeaveAccrualDaysPerMonth());
        }
        if (dto.getMinServiceMonthsForAnnualLeave() != null) {
            company.setMinServiceMonthsForAnnualLeave(dto.getMinServiceMonthsForAnnualLeave());
        }
        if (dto.getRetirementCompensationEnabled() != null) {
            company.setRetirementCompensationEnabled(dto.getRetirementCompensationEnabled());
        }
        if (dto.getRetirementCompensationMonthsPerYear() != null) {
            company.setRetirementCompensationMonthsPerYear(dto.getRetirementCompensationMonthsPerYear());
        }
        if (dto.getLoanPolicyEnabled() != null) {
            company.setLoanPolicyEnabled(dto.getLoanPolicyEnabled());
        }
        if (dto.getLoanMinServiceDays() != null) {
            company.setLoanMinServiceDays(Math.max(dto.getLoanMinServiceDays(), 0));
        }
        if (dto.getLoanMaxRepaymentMonths() != null) {
            company.setLoanMaxRepaymentMonths(Math.max(dto.getLoanMaxRepaymentMonths(), 1));
        }
        if (dto.getStandardWorkingHoursPerDay() != null) {
            // Clamp to a sane 1–24h window so payroll/attendance math stays valid.
            BigDecimal hrs = dto.getStandardWorkingHoursPerDay();
            if (hrs.compareTo(BigDecimal.ONE) < 0) hrs = BigDecimal.ONE;
            if (hrs.compareTo(new BigDecimal("24")) > 0) hrs = new BigDecimal("24");
            company.setStandardWorkingHoursPerDay(hrs);
        }
        if (dto.getRequireCheckIn() != null) {
            company.setRequireCheckIn(dto.getRequireCheckIn());
        }
        if (dto.getOtDayRateMultiplier() != null) {
            company.setOtDayRateMultiplier(clampMultiplier(dto.getOtDayRateMultiplier()));
        }
        if (dto.getOtNightFridayHolidayRateMultiplier() != null) {
            company.setOtNightFridayHolidayRateMultiplier(
                    clampMultiplier(dto.getOtNightFridayHolidayRateMultiplier()));
        }
        if (dto.getOtNightStartTime() != null) {
            company.setOtNightStartTime(dto.getOtNightStartTime());
        }
        if (dto.getOtNightEndTime() != null) {
            company.setOtNightEndTime(dto.getOtNightEndTime());
        }
        if (dto.getOtMaxHoursPerDay() != null) {
            BigDecimal cap = dto.getOtMaxHoursPerDay();
            if (cap.compareTo(BigDecimal.ZERO) < 0) cap = BigDecimal.ZERO;
            if (cap.compareTo(new BigDecimal("24")) > 0) cap = new BigDecimal("24");
            company.setOtMaxHoursPerDay(cap);
        }
        if (dto.getMinimumMonthlyWage() != null) {
            company.setMinimumMonthlyWage(nonNegative(dto.getMinimumMonthlyWage()));
        }
        boolean statutoryAllowanceChanged = false;
        if (dto.getDefaultHousingAllowance() != null) {
            company.setDefaultHousingAllowance(nonNegative(dto.getDefaultHousingAllowance()));
            statutoryAllowanceChanged = true;
        }
        if (dto.getDefaultFoodAllowance() != null) {
            company.setDefaultFoodAllowance(nonNegative(dto.getDefaultFoodAllowance()));
            statutoryAllowanceChanged = true;
        }

        companyRepository.save(company);
        if (statutoryAllowanceChanged) {
            employeeCompensationService.syncFollowingAllowancesFromCompany(companyId);
        }
        return toHrPoliciesDto(company);
    }

    private static BigDecimal clampMultiplier(BigDecimal value) {
        if (value.compareTo(BigDecimal.ONE) < 0) return BigDecimal.ONE;
        if (value.compareTo(new BigDecimal("5")) > 0) return new BigDecimal("5");
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private HrPoliciesDTO toHrPoliciesDto(Company company) {
        return HrPoliciesDTO.builder()
                .annualLeaveAccrualEnabled(company.isAnnualLeaveAccrualEnabled())
                .annualLeaveAccrualDaysPerMonth(
                        company.getAnnualLeaveAccrualDaysPerMonth() != null
                                ? company.getAnnualLeaveAccrualDaysPerMonth()
                                : new BigDecimal("1.50"))
                .minServiceMonthsForAnnualLeave(
                        company.getMinServiceMonthsForAnnualLeave() != null
                                ? company.getMinServiceMonthsForAnnualLeave()
                                : 6)
                .retirementCompensationEnabled(company.isRetirementCompensationEnabled())
                .retirementCompensationMonthsPerYear(
                        company.getRetirementCompensationMonthsPerYear() != null
                                ? company.getRetirementCompensationMonthsPerYear()
                                : new BigDecimal("1.00"))
                .loanPolicyEnabled(company.isLoanPolicyEnabled())
                .loanMinServiceDays(
                        company.getLoanMinServiceDays() != null
                                ? company.getLoanMinServiceDays()
                                : 365)
                .loanMaxRepaymentMonths(
                        company.getLoanMaxRepaymentMonths() != null
                                ? company.getLoanMaxRepaymentMonths()
                                : 24)
                .standardWorkingHoursPerDay(
                        company.getStandardWorkingHoursPerDay() != null
                                ? company.getStandardWorkingHoursPerDay()
                                : new BigDecimal("6.00"))
                .requireCheckIn(company.isRequireCheckIn())
                .otDayRateMultiplier(
                        company.getOtDayRateMultiplier() != null
                                ? company.getOtDayRateMultiplier()
                                : QatarLaborLawDefaultsService.OT_DAY_MULTIPLIER)
                .otNightFridayHolidayRateMultiplier(
                        company.getOtNightFridayHolidayRateMultiplier() != null
                                ? company.getOtNightFridayHolidayRateMultiplier()
                                : QatarLaborLawDefaultsService.OT_NIGHT_FRIDAY_HOLIDAY_MULTIPLIER)
                .otNightStartTime(
                        company.getOtNightStartTime() != null
                                ? company.getOtNightStartTime()
                                : QatarLaborLawDefaultsService.OT_NIGHT_START)
                .otNightEndTime(
                        company.getOtNightEndTime() != null
                                ? company.getOtNightEndTime()
                                : QatarLaborLawDefaultsService.OT_NIGHT_END)
                .otMaxHoursPerDay(
                        company.getOtMaxHoursPerDay() != null
                                ? company.getOtMaxHoursPerDay()
                                : QatarLaborLawDefaultsService.OT_MAX_HOURS_PER_DAY)
                .minimumMonthlyWage(
                        company.getMinimumMonthlyWage() != null
                                ? company.getMinimumMonthlyWage()
                                : QatarLaborLawDefaultsService.MINIMUM_MONTHLY_WAGE)
                .defaultHousingAllowance(
                        company.getDefaultHousingAllowance() != null
                                ? company.getDefaultHousingAllowance()
                                : QatarLaborLawDefaultsService.DEFAULT_HOUSING_ALLOWANCE)
                .defaultFoodAllowance(
                        company.getDefaultFoodAllowance() != null
                                ? company.getDefaultFoodAllowance()
                                : QatarLaborLawDefaultsService.DEFAULT_FOOD_ALLOWANCE)
                .build();
    }

    // ======================================================
    // PAYROLL BANK FILE (SIF) EXPORT SETTINGS
    // ======================================================
    public PayrollExportSettingsDTO getPayrollExportSettings(Long companyId) {
        Company company = getCompanyById(companyId);
        return toPayrollExportSettingsDto(company);
    }

    @Transactional
    public PayrollExportSettingsDTO updatePayrollExportSettings(Long companyId, PayrollExportSettingsDTO dto) {
        Company company = getCompanyById(companyId);
        company.setPayrollEmployerEid(trimToNull(dto.getPayrollEmployerEid()));
        company.setPayrollPayerEid(trimToNull(dto.getPayrollPayerEid()));
        company.setPayrollPayerQid(trimToNull(dto.getPayrollPayerQid()));
        company.setPayrollPayerBankShortName(trimToNull(dto.getPayrollPayerBankShortName()));
        company.setPayrollPayerIban(trimToNull(dto.getPayrollPayerIban()));
        String sif = trimToNull(dto.getPayrollSifVersion());
        company.setPayrollSifVersion(sif != null ? sif : "1");
        companyRepository.save(company);
        return toPayrollExportSettingsDto(company);
    }

    private PayrollExportSettingsDTO toPayrollExportSettingsDto(Company company) {
        return PayrollExportSettingsDTO.builder()
                .payrollEmployerEid(company.getPayrollEmployerEid())
                .payrollPayerEid(company.getPayrollPayerEid())
                .payrollPayerQid(company.getPayrollPayerQid())
                .payrollPayerBankShortName(company.getPayrollPayerBankShortName())
                .payrollPayerIban(company.getPayrollPayerIban())
                .payrollSifVersion(
                        company.getPayrollSifVersion() != null ? company.getPayrollSifVersion() : "1")
                .build();
    }

    /** Seed baseline HR roles so new companies can assign employees immediately. */
    private void seedDefaultCompanyRoles(Company company) {
        createRoleIfAbsent(company, "Admin", "Company administrator");
        createRoleIfAbsent(company, "Employee", "Standard employee");
        createRoleIfAbsent(company, "HR", "Human resources");
    }

    private void createRoleIfAbsent(Company company, String name, String description) {
        if (companyRoleRepository.existsByCompanyIdAndName(company.getId(), name)) {
            return;
        }
        companyRoleRepository.save(CompanyRole.builder()
                .name(name)
                .description(description)
                .active(true)
                .company(company)
                .build());
    }

    // ======================================================
    // DEACTIVATE / REACTIVATE COMPANY (soft delete — never a hard delete)
    // ======================================================
    @Transactional
    public void deleteCompany(Long id) {
        Company existing = getCompanyById(id);
        existing.setActive(false);
        companyRepository.save(existing);
    }

    @Transactional
    public Company reactivateCompany(Long id) {
        Company existing = getCompanyById(id);
        existing.setActive(true);
        return hydrateInvoiceBrandingView(companyRepository.save(existing));
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