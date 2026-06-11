package com.erp.repo;

import com.erp.domain.ResidencePermit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResidencePermitRepository extends JpaRepository<ResidencePermit, Long> {

    Optional<ResidencePermit> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    // Company-scoped expiry report: permits ending on/before the cutoff.
    // Rows with a null end date are naturally excluded by the comparison.
    @EntityGraph(attributePaths = "employee")
    List<ResidencePermit> findByEmployee_Company_IdAndEndDateLessThanEqual(
            Long companyId, LocalDate cutoff);
}
