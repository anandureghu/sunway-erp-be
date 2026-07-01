package com.erp.repo.inventory;

import com.erp.domain.inventory.DispatchCarrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispatchCarrierRepository extends JpaRepository<DispatchCarrier, Long> {

    List<DispatchCarrier> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<DispatchCarrier> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByNameIgnoreCaseAndCompanyId(String name, Long companyId);

    boolean existsByNameIgnoreCaseAndCompanyIdAndIdNot(String name, Long companyId, Long id);
}
