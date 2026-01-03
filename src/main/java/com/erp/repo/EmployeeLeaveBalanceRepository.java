package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveBalanceRepository
        extends JpaRepository<EmployeeLeaveBalance, Long> {

    Optional<EmployeeLeaveBalance> findByEmployeeAndLeaveType(
            Employee employee, String leaveType);

    List<EmployeeLeaveBalance> findByEmployee(Employee employee);
}
