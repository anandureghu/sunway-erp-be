package com.erp.repo.inventory;

import com.erp.domain.inventory.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    boolean existsByCodeAndCompanyId(String code, Long companyId);

    Optional<Warehouse> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
