package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.sales.ShipmentCreateDTO;
import com.erp.dto.sales.ShipmentDeliverDTO;
import com.erp.dto.sales.ShipmentResponseDTO;
import com.erp.dto.sales.ShipmentTrackingEventCreateDTO;
import com.erp.service.sales.ShipmentService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse/shipments")
public class ShipmentController {

    private final ShipmentService service;

    public ShipmentController(ShipmentService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE})
    @PostMapping("/from-picklist/{picklistId}")
    public ShipmentResponseDTO create(
            @PathVariable("picklistId") Long picklistId,
            @RequestBody ShipmentCreateDTO dto
    ) {
        return service.create(picklistId, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/dispatch")
    public ShipmentResponseDTO dispatch(@PathVariable("id") Long id) {
        return service.dispatch(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/in-transit")
    public ShipmentResponseDTO markInTransit(@PathVariable("id") Long id) {
        return service.markInTransit(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/delivered")
    public ShipmentResponseDTO markDelivered(
            @PathVariable("id") Long id,
            @RequestBody ShipmentDeliverDTO dto
    ) {
        return service.markDelivered(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/out-for-delivery")
    public ShipmentResponseDTO markOutForDelivery(@PathVariable("id") Long id) {
        return service.markOutForDelivery(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/failed-delivery")
    public ShipmentResponseDTO markFailedDelivery(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ShipmentTrackingEventCreateDTO dto
    ) {
        return service.markFailedDelivery(id, dto == null ? null : dto.getNotes());
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT, AppAction.DELETE})
    @PostMapping("/{id}/cancel")
    public ShipmentResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/tracking-events")
    public ShipmentResponseDTO addTrackingEvent(
            @PathVariable("id") Long id,
            @RequestBody ShipmentTrackingEventCreateDTO dto
    ) {
        return service.addTrackingUpdate(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ShipmentResponseDTO updateDetails(
            @PathVariable("id") Long id,
            @RequestBody ShipmentCreateDTO dto
    ) {
        return service.updateDetails(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ShipmentResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<ShipmentResponseDTO> list() {
        return service.list();
    }
}
