package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.VendorCreateDTO;
import com.erp.dto.inventory.VendorFilterDTO;
import com.erp.dto.inventory.VendorResponseDTO;
import com.erp.dto.inventory.VendorUpdateDTO;
import com.erp.service.inventory.VendorService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public Page<VendorResponseDTO> getVendors(
            VendorFilterDTO filter,
            Pageable pageable
    ) {
        return vendorService.getFilteredVendors(filter, pageable);
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ResponseEntity<VendorResponseDTO> getVendorById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(vendorService.getVendorDTO(id));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<VendorResponseDTO> createVendor(@RequestBody VendorCreateDTO dto) {
        return ResponseEntity.ok(vendorService.createVendor(dto));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.APPROVE, AppAction.EDIT})
    @PatchMapping("/{id}")
    public ResponseEntity<Boolean> approveVendor(@PathVariable("id") Long id,
                                                 @RequestParam(name = "status", required = false) Boolean status) {
        return ResponseEntity.ok(vendorService.approveVendor(id, status));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<VendorResponseDTO> updateVendor(
            @PathVariable("id") Long id,
            @RequestBody VendorUpdateDTO dto
    ) {
        return ResponseEntity.ok(vendorService.updateVendor(id, dto));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVendor(@PathVariable("id") Long id) {
        vendorService.deleteVendor(id);
        return ResponseEntity.noContent().build();
    }
}
