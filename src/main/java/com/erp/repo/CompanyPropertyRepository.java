package com.erp.repo;


import com.erp.domain.CompanyProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyPropertyRepository
        extends JpaRepository<CompanyProperty, Long> {

    List<CompanyProperty> findByEmployeeId(Long employee);

    boolean existsByEmployeeIdAndItemCode(Long employeeId, String itemCode);
}
