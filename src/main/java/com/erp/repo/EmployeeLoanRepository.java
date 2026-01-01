package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeLoanRepository
        extends JpaRepository<EmployeeLoan, Long> {

    List<EmployeeLoan> findByEmployee(Employee employee);

    List<EmployeeLoan> findByEmployeeAndStatus(
            Employee employee,
            String status
    );
}
