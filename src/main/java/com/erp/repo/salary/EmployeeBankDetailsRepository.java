package com.erp.repo.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.EmployeeBankDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface EmployeeBankDetailsRepository
        extends JpaRepository<EmployeeBankDetails, Long> {

    Optional<EmployeeBankDetails> findByEmployee(Employee employee);
}
