package com.erp.repo.hr;

import com.erp.domain.hr.CompanyInvoiceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyInvoiceSettingsRepository extends JpaRepository<CompanyInvoiceSettings, Long> {
    Optional<CompanyInvoiceSettings> findByCompanyId(Long companyId);
}
