package com.erp.repo.contact;

import com.erp.domain.EmployeeContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeContactInfoRepository
        extends JpaRepository<EmployeeContactInfo, Long> {

    Optional<EmployeeContactInfo> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
