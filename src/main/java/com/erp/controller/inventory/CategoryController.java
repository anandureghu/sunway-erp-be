package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.CategoryCreateDTO;
import com.erp.dto.inventory.CategoryCsvImportResultDTO;
import com.erp.dto.inventory.CategoryCsvPreviewDTO;
import com.erp.dto.inventory.CategoryResponseDTO;
import com.erp.dto.inventory.CategoryUpdateDTO;
import com.erp.service.inventory.CategoryCsvImportService;
import com.erp.service.inventory.CategoryService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/categories")
public class CategoryController {

    private final CategoryService service;
    private final CategoryCsvImportService csvImportService;

    public CategoryController(CategoryService service, CategoryCsvImportService csvImportService) {
        this.service = service;
        this.csvImportService = csvImportService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.CREATE})
    @PostMapping
    public CategoryResponseDTO create(@RequestBody CategoryCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public CategoryResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody CategoryUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public CategoryResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<CategoryResponseDTO> listCategories() {
        return service.listCategories();
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/children")
    public List<CategoryResponseDTO> listSubCategories(@PathVariable("id") Long id) {
        return service.listSubCategories(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.CREATE})
    @PostMapping(value = "/import-csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CategoryCsvPreviewDTO previewImportCsv(@RequestPart("file") MultipartFile file) {
        return csvImportService.preview(file);
    }

    @RequiresPermission(module = AppModule.INVENTORY_CATEGORY, action = {AppAction.CREATE})
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CategoryCsvImportResultDTO importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping
    ) {
        return csvImportService.importCsv(file, mapping);
    }
}
