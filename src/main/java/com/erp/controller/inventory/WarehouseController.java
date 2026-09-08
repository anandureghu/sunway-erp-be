package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.WarehouseCreateDTO;
import com.erp.dto.inventory.WarehouseCsvImportResultDTO;
import com.erp.dto.inventory.WarehouseCsvPreviewDTO;
import com.erp.dto.inventory.WarehouseResponseDTO;
import com.erp.dto.inventory.WarehouseUpdateDTO;
import com.erp.service.inventory.WarehouseCsvImportService;
import com.erp.service.inventory.WarehouseService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/warehouses")
public class WarehouseController {

    private final WarehouseService service;
    private final WarehouseCsvImportService csvImportService;

    public WarehouseController(WarehouseService service, WarehouseCsvImportService csvImportService) {
        this.service = service;
        this.csvImportService = csvImportService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.CREATE})
    @PostMapping
    public WarehouseResponseDTO create(@RequestBody WarehouseCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public WarehouseResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody WarehouseUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public WarehouseResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<WarehouseResponseDTO> list() {
        return service.list();
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.CREATE})
    @PostMapping(value = "/import-csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WarehouseCsvPreviewDTO previewImportCsv(@RequestPart("file") MultipartFile file) {
        return csvImportService.preview(file);
    }

    @RequiresPermission(module = AppModule.INVENTORY_WAREHOUSE, action = {AppAction.CREATE})
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WarehouseCsvImportResultDTO importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping
    ) {
        return csvImportService.importCsv(file, mapping);
    }
}
