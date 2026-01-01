package com.erp.repo;

import com.erp.domain.Passport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PassportRepository extends JpaRepository<Passport, Long> {

    Optional<Passport> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
