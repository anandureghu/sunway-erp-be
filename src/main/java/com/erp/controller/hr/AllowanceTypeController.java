package com.erp.controller.hr;

import com.erp.dto.hr.AllowanceTypeRequestDTO;
import com.erp.dto.hr.AllowanceTypeResponseDTO;
import com.erp.service.common.AllowanceTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hr/allowance-types")
@RequiredArgsConstructor
public class AllowanceTypeController {

    private final AllowanceTypeService service;

    // ================= CREATE =================

    @PostMapping
    public ResponseEntity<AllowanceTypeResponseDTO> create(
            @Valid @RequestBody AllowanceTypeRequestDTO request) {

        return ResponseEntity.ok(service.create(request));
    }

    // ================= GET ACTIVE =================

    @GetMapping
    public ResponseEntity<List<AllowanceTypeResponseDTO>> getActive() {

        return ResponseEntity.ok(service.getActiveTypes());
    }

    // ================= UPDATE =================

    @PutMapping("/{id}")
    public ResponseEntity<AllowanceTypeResponseDTO> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody AllowanceTypeRequestDTO request) {

        return ResponseEntity.ok(service.update(id, request));
    }

    // ================= DEACTIVATE =================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable("id") Long id) {

        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}