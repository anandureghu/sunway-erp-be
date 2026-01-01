package com.erp.repo;

import com.erp.domain.EmployeeAppraisal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface EmployeeAppraisalRepository
        extends JpaRepository<EmployeeAppraisal, Long> {

    Optional<EmployeeAppraisal> findByEmployeeIdAndMonthAndYear(
            Long employeeId,
            String month,
            Integer year
    );

    boolean existsByEmployeeIdAndMonthAndYear(
            Long employeeId,
            String month,
            Integer year
    );

    boolean existsByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    void deleteByEmployeeIdAndMonthAndYear(
            Long employeeId,
            String month,
            Integer year
    );
}
