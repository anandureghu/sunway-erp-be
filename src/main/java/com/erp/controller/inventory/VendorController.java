package com.erp.controller.inventory;

import com.erp.dto.inventory.VendorCreateDTO;
import com.erp.dto.inventory.VendorResponseDTO;
import com.erp.dto.inventory.VendorUpdateDTO;
import com.erp.service.inventory.VendorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    // ---------------- GET ALL ----------------
    @GetMapping
    public List<VendorResponseDTO> getAllVendors() {
        return vendorService.getAllVendors();
    }

    // ---------------- GET BY ID ----------------
    @GetMapping("/{id}")
    public ResponseEntity<VendorResponseDTO> getVendorById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(vendorService.getVendorDTO(id));
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<VendorResponseDTO> createVendor(@RequestBody VendorCreateDTO dto) {
        return ResponseEntity.ok(vendorService.createVendor(dto));
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{id}")
    public ResponseEntity<VendorResponseDTO> updateVendor(
            @PathVariable("id") Long id,
            @RequestBody VendorUpdateDTO dto
    ) {
        return ResponseEntity.ok(vendorService.updateVendor(id, dto));
    }

    // ---------------- DELETE ----------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVendor(@PathVariable("id") Long id) {
        vendorService.deleteVendor(id);
        return ResponseEntity.noContent().build();
    }
}
