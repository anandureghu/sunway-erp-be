package com.erp.repo.inventory;

import com.erp.domain.inventory.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByCompanyId(Long companyId);

    boolean existsByCodeAndCompanyId(String code, Long companyId);
}
