package com.erp.repo;

import com.erp.domain.ResidencePermit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResidencePermitRepository extends JpaRepository<ResidencePermit, Long> {

    Optional<ResidencePermit> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);
}
