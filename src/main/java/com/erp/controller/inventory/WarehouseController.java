package com.erp.controller.inventory;

import com.erp.dto.inventory.WarehouseCreateDTO;
import com.erp.dto.inventory.WarehouseResponseDTO;
import com.erp.dto.inventory.WarehouseUpdateDTO;
import com.erp.service.inventory.WarehouseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @PostMapping
    public WarehouseResponseDTO create(@RequestBody WarehouseCreateDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public WarehouseResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody WarehouseUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @GetMapping("/{id}")
    public WarehouseResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<WarehouseResponseDTO> list() {
        return service.list();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
