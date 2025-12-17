package com.erp.controller.inventory;

import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.service.inventory.ItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping
    public ItemResponseDTO create(@RequestBody ItemCreateDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ItemResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody ItemUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @GetMapping
    public List<ItemResponseDTO> list() {
        return service.listForCompany();
    }

    @GetMapping("/{id}")
    public ItemResponseDTO get(@PathVariable("id") Long id) {
        return service.getItem(id);
    }
}
