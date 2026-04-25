package com.erp.repo.sales;

import com.erp.domain.sales.ShipmentTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {

    List<ShipmentTrackingEvent> findByShipmentIdOrderByEventAtAscIdAsc(Long shipmentId);
}
