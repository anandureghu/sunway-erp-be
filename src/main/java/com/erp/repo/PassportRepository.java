package com.erp.repo;

import com.erp.domain.Passport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PassportRepository extends JpaRepository<Passport, Long> {

    Optional<Passport> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    // Company-scoped expiry report: passports expiring on/before the cutoff.
    // Rows with a null expiry date are naturally excluded by the comparison.
    @EntityGraph(attributePaths = "employee")
    List<Passport> findByEmployee_Company_IdAndExpiryDateLessThanEqual(
            Long companyId, LocalDate cutoff);
}
