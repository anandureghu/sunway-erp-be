package com.erp.repo.sales;

import com.erp.domain.sales.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByPicklistId(Long picklistId);

    List<Shipment> findByCompanyId(Long companyId);
}
