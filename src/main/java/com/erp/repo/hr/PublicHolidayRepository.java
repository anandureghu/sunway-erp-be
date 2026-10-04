package com.erp.repo.hr;

import com.erp.domain.hr.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    /** Holidays overlapping [from, to]: pass (companyId, to, from). */
    List<PublicHoliday> findByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
            Long companyId, LocalDate to, LocalDate from);
}
