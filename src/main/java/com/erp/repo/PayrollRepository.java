package com.erp.repo;

import com.erp.domain.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByEmployeeIdOrderByPayPeriodDesc(Long employeeId);
    Optional<Payroll> findFirstByEmployeeIdOrderByPayPeriodDesc(Long employeeId);
}
