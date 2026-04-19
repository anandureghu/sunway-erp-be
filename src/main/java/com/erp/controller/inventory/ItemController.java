package com.erp.controller.inventory;

import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemStockAdjustDTO;
import com.erp.dto.inventory.ItemStockReceiveDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.dto.inventory.ItemWarehouseStockRowDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.inventory.ItemService;
import com.erp.service.inventory.ItemWarehouseStockService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/items")
public class ItemController {

    private final ItemService service;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final AuthContext auth;

    public ItemController(
            ItemService service,
            FileStorageService fileStorageService,
            ItemWarehouseStockService itemWarehouseStockService,
            AuthContext auth
    ) {
        this.service = service;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.auth = auth;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO createItem(
            @RequestPart("data") ItemCreateDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return service.create(dto, image);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ItemResponseDTO updateJson(
            @PathVariable("id") Long id,
            @RequestBody ItemUpdateDTO dto
    ) {
        return service.update(id, dto, null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO updateMultipart(
            @PathVariable("id") Long id,
            @RequestPart("data") ItemUpdateDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return service.update(id, dto, image);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO updateItemImage(
            @PathVariable("id") Long id,
            @RequestPart("image") MultipartFile image
    ) {
        return service.updateItemImage(id, image);
    }

    @GetMapping
    public List<ItemResponseDTO> list() {
        return service.listForCompany();
    }

    @GetMapping("/{id}")
    public ItemResponseDTO get(@PathVariable("id") Long id) {
        return service.getItem(id);
    }

    @GetMapping("/{id}/warehouse-stock")
    public List<ItemWarehouseStockRowDTO> listWarehouseStock(@PathVariable("id") Long id) {
        return itemWarehouseStockService.listStockForItem(id, auth.getCurrentCompanyId());
    }

    @PostMapping("/{id}/stock/receive")
    public ItemResponseDTO receiveStock(
            @PathVariable("id") Long id,
            @RequestBody ItemStockReceiveDTO dto
    ) {
        return service.receiveStock(id, dto);
    }

    @PostMapping("/{id}/stock/adjust")
    public ItemResponseDTO adjustStock(
            @PathVariable("id") Long id,
            @RequestBody ItemStockAdjustDTO dto
    ) {
        return service.adjustStock(id, dto);
    }
}
