package com.erp.repo;

import com.erp.domain.CompanyNumberingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyNumberingConfigRepository extends JpaRepository<CompanyNumberingConfig, Long> {

    List<CompanyNumberingConfig> findByCompanyId(Long companyId);

    Optional<CompanyNumberingConfig> findByCompanyIdAndDocType(Long companyId, String docType);
}
