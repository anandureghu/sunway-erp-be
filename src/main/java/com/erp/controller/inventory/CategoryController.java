package com.erp.controller.inventory;

import com.erp.dto.inventory.CategoryCreateDTO;
import com.erp.dto.inventory.CategoryResponseDTO;
import com.erp.dto.inventory.CategoryUpdateDTO;
import com.erp.service.inventory.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    // Create category or subcategory
    @PostMapping
    public CategoryResponseDTO create(@RequestBody CategoryCreateDTO dto) {
        return service.create(dto);
    }

    // Update
    @PutMapping("/{id}")
    public CategoryResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody CategoryUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    // Get single
    @GetMapping("/{id}")
    public CategoryResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    // List top-level categories
    @GetMapping
    public List<CategoryResponseDTO> listCategories() {
        return service.listCategories();
    }

    // List subcategories
    @GetMapping("/{id}/children")
    public List<CategoryResponseDTO> listSubCategories(@PathVariable("id") Long id) {
        return service.listSubCategories(id);
    }

    // Delete
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
