package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.DispatchCarrierCreateDTO;
import com.erp.dto.inventory.DispatchCarrierResponseDTO;
import com.erp.dto.inventory.DispatchCarrierUpdateDTO;
import com.erp.service.inventory.DispatchCarrierService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/carriers")
public class DispatchCarrierController {

    private final DispatchCarrierService service;

    public DispatchCarrierController(DispatchCarrierService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.CREATE})
    @PostMapping
    public DispatchCarrierResponseDTO create(@RequestBody DispatchCarrierCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public DispatchCarrierResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody DispatchCarrierUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public DispatchCarrierResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<DispatchCarrierResponseDTO> list(
            @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly
    ) {
        return activeOnly ? service.listActive() : service.list();
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
