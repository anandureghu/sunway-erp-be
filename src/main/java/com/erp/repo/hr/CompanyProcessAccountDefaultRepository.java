package com.erp.repo.hr;

import com.erp.domain.finance.AccountingProcessCode;
import com.erp.domain.hr.CompanyProcessAccountDefault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyProcessAccountDefaultRepository extends JpaRepository<CompanyProcessAccountDefault, Long> {

    List<CompanyProcessAccountDefault> findByCompanyIdOrderByProcessCodeAsc(Long companyId);

    Optional<CompanyProcessAccountDefault> findByCompanyIdAndProcessCode(
            Long companyId, AccountingProcessCode processCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CompanyProcessAccountDefault e where e.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);
}
