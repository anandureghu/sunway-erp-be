package com.erp.repo;

import com.erp.domain.EmployeeAppraisal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeAppraisalRepository
        extends JpaRepository<EmployeeAppraisal, Long> {

    List<EmployeeAppraisal> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);

    void deleteByIdAndEmployeeId(Long id, Long employeeId);

    boolean existsByIdAndEmployeeId(Long appraisalId, Long employeeId);

    Optional<EmployeeAppraisal> findByIdAndEmployeeId(Long appraisalId, Long employeeId);
}
