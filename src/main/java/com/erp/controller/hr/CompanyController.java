package com.erp.controller.hr;

import com.erp.domain.hr.Company;
import com.erp.dto.hr.AccountingDefaultsDTO;
import com.erp.dto.hr.CompanyDTO;
import com.erp.dto.hr.HrPoliciesDTO;
import com.erp.dto.hr.InvoiceBrandingSettingsDTO;
import com.erp.dto.hr.PayrollExportSettingsDTO;
import com.erp.service.hr.CompanyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyService.getAllCompanies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(companyService.getCompanyById(id));
    }

    @PostMapping
    public ResponseEntity<Company> createCompany(
            @RequestBody CompanyDTO company) {

        Company saved = companyService.createCompany(company, null);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Company> createCompanyMultipart(
            @RequestPart("data") CompanyDTO company,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {

        Company saved = companyService.createCompany(company, logo);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public Company updateCompany(@PathVariable("id") Long id, @RequestBody CompanyDTO updated) {
        return companyService.updateCompany(id, updated, null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Company updateCompanyMultipart(
            @PathVariable("id") Long id,
            @RequestPart("data") CompanyDTO updated,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return companyService.updateCompany(id, updated, logo);
    }

    @PutMapping("/{id}/accounting-defaults")
    public Company updateAccountingDefaults(
            @PathVariable("id") Long id,
            @RequestBody AccountingDefaultsDTO body) {
        return companyService.updateAccountingDefaults(id, body);
    }

    @PutMapping("/{id}/invoice-branding")
    public Company updateInvoiceBranding(
            @PathVariable("id") Long id,
            @RequestBody InvoiceBrandingSettingsDTO body) {
        return companyService.updateInvoiceBrandingSettings(id, body);
    }

    @GetMapping("/{id}/hr-policies")
    public ResponseEntity<HrPoliciesDTO> getHrPolicies(@PathVariable("id") Long id) {
        return ResponseEntity.ok(companyService.getHrPolicies(id));
    }

    @PutMapping("/{id}/hr-policies")
    public ResponseEntity<HrPoliciesDTO> updateHrPolicies(
            @PathVariable("id") Long id,
            @RequestBody HrPoliciesDTO body) {
        return ResponseEntity.ok(companyService.updateHrPolicies(id, body));
    }

    @GetMapping("/{id}/payroll-export-settings")
    public ResponseEntity<PayrollExportSettingsDTO> getPayrollExportSettings(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(companyService.getPayrollExportSettings(id));
    }

    @PutMapping("/{id}/payroll-export-settings")
    public ResponseEntity<PayrollExportSettingsDTO> updatePayrollExportSettings(
            @PathVariable("id") Long id,
            @RequestBody PayrollExportSettingsDTO body) {
        return ResponseEntity.ok(companyService.updatePayrollExportSettings(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable("id") Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}
