package com.erp.controller.inventory;

import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.inventory.ItemService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/items")
public class ItemController {

    private final ItemService service;
    private final AuthContext auth;

    public ItemController(ItemService service, FileStorageService fileStorageService, AuthContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO createItem(
            @RequestPart("data") ItemCreateDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return service.create(dto, image);
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
