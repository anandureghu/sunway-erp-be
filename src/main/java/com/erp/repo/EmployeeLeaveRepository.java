package com.erp.repo;

import com.erp.domain.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeLeaveRepository
        extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByEmployeeIdOrderByDateReportedDesc(Long employeeId);
}
