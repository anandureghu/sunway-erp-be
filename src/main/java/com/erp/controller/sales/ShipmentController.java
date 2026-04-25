package com.erp.controller.sales;

import com.erp.dto.sales.ShipmentCreateDTO;
import com.erp.dto.sales.ShipmentResponseDTO;
import com.erp.dto.sales.ShipmentTrackingEventCreateDTO;
import com.erp.service.sales.ShipmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse/shipments")
public class ShipmentController {

    private final ShipmentService service;

    public ShipmentController(ShipmentService service) {
        this.service = service;
    }

    @PostMapping("/from-picklist/{picklistId}")
    public ShipmentResponseDTO create(
            @PathVariable("picklistId") Long picklistId,
            @RequestBody ShipmentCreateDTO dto
    ) {
        return service.create(picklistId, dto);
    }

    @PostMapping("/{id}/dispatch")
    public ShipmentResponseDTO dispatch(@PathVariable("id") Long id) {
        return service.dispatch(id);
    }

    @PostMapping("/{id}/in-transit")
    public ShipmentResponseDTO markInTransit(@PathVariable("id") Long id) {
        return service.markInTransit(id);
    }

    @PostMapping("/{id}/delivered")
    public ShipmentResponseDTO markDelivered(@PathVariable("id") Long id) {
        return service.markDelivered(id);
    }

    @PostMapping("/{id}/out-for-delivery")
    public ShipmentResponseDTO markOutForDelivery(@PathVariable("id") Long id) {
        return service.markOutForDelivery(id);
    }

    @PostMapping("/{id}/failed-delivery")
    public ShipmentResponseDTO markFailedDelivery(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ShipmentTrackingEventCreateDTO dto
    ) {
        return service.markFailedDelivery(id, dto == null ? null : dto.getNotes());
    }

    @PostMapping("/{id}/cancel")
    public ShipmentResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/tracking-events")
    public ShipmentResponseDTO addTrackingEvent(
            @PathVariable("id") Long id,
            @RequestBody ShipmentTrackingEventCreateDTO dto
    ) {
        return service.addTrackingUpdate(id, dto);
    }

    @PutMapping("/{id}")
    public ShipmentResponseDTO updateDetails(
            @PathVariable("id") Long id,
            @RequestBody ShipmentCreateDTO dto
    ) {
        return service.updateDetails(id, dto);
    }

    @GetMapping("/{id}")
    public ShipmentResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<ShipmentResponseDTO> list() {
        return service.list();
    }
}
