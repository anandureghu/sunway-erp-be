package com.erp.repo;

import com.erp.domain.EmployeeOvertimeOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeOvertimeOverrideRepository extends JpaRepository<EmployeeOvertimeOverride, Long> {

    Optional<EmployeeOvertimeOverride> findByEmployee_IdAndYearAndMonth(Long employeeId, int year, int month);

    List<EmployeeOvertimeOverride> findByEmployee_IdInAndYearAndMonth(List<Long> employeeIds, int year, int month);
}
