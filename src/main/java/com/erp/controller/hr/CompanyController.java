package com.erp.controller.hr;

import com.erp.domain.hr.Company;
import com.erp.dto.hr.AccountingDefaultsDTO;
import com.erp.dto.hr.CompanyDTO;
import com.erp.dto.hr.InvoiceBrandingSettingsDTO;
import com.erp.service.hr.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        Company saved = companyService.createCompany(company);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public Company updateCompany(@PathVariable("id") Long id, @RequestBody CompanyDTO updated) {
        return companyService.updateCompany(id, updated);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable("id") Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}
