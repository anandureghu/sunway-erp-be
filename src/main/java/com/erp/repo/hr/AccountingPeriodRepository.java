package com.erp.repo.hr;


import com.erp.domain.hr.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    Optional<AccountingPeriod>
    findByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long companyId,
            LocalDate date1,
            LocalDate date2
    );

    List<AccountingPeriod> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long companyId,
            LocalDate start,
            LocalDate end
    );
}