package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {

    List<EmployeeLoan> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    List<EmployeeLoan> findByEmployeeIdAndStatus(Long employeeId, String status);

    Optional<EmployeeLoan> findByLoanCode(String loanCode);

    List<EmployeeLoan> findByEmployee(Employee employee);

    List<EmployeeLoan> findByEmployeeId(Long employeeId);

    List<EmployeeLoan> findByEmployeeAndStatus(Employee employee, String active);
}