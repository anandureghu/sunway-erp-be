package com.erp.repo.contact;

import com.erp.domain.EmployeeAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeAddressRepository
        extends JpaRepository<EmployeeAddress, Long> {
    List<EmployeeAddress> findByEmployeeId(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
