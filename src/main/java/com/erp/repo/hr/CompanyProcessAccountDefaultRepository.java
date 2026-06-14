package com.erp.repo.hr;

import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.hr.CompanyProcessAccountDefault;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyProcessAccountDefaultRepository extends JpaRepository<CompanyProcessAccountDefault, Long> {

    List<CompanyProcessAccountDefault> findByCompanyIdOrderByProcessCodeAsc(Long companyId);

    Optional<CompanyProcessAccountDefault> findByCompanyIdAndProcessCode(
            Long companyId, AccountingProcessCode processCode);

    void deleteByCompanyId(Long companyId);
}
