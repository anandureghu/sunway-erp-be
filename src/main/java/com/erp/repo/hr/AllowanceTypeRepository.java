package com.erp.repo.hr;

import com.erp.domain.hr.AllowanceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllowanceTypeRepository extends JpaRepository<AllowanceType, Long> {

    Optional<AllowanceType> findByName(String name);

    List<AllowanceType> findByActiveTrue();

    Optional<AllowanceType> findByNameIgnoreCase(String name);
}