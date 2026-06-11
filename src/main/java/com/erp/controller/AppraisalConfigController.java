package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.appraisal.AppraisalConfigRequestDTO;
import com.erp.dto.appraisal.AppraisalConfigResponseDTO;
import com.erp.service.appraisal.AppraisalConfigService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/appraisal-config")
public class AppraisalConfigController {

    private final AppraisalConfigService configService;

    /* ================= GET ACTIVE ================= */

    @RequiresPermission(
            module = AppModule.APPRAISAL,
            action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL}
    )
    @GetMapping("/active")
    public ResponseEntity<AppraisalConfigResponseDTO> getActive() {
        return configService.getActive()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ================= GET BY YEAR ================= */

    @RequiresPermission(
            module = AppModule.APPRAISAL,
            action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL}
    )
    @GetMapping("/year/{year}")
    public ResponseEntity<AppraisalConfigResponseDTO> getByYear(
            @PathVariable("year") Integer year
    ) {
        return configService.getByYear(year)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /* ================= LIST CYCLES FOR A YEAR ================= */

    @RequiresPermission(
            module = AppModule.APPRAISAL,
            action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL}
    )
    @GetMapping("/cycles")
    public ResponseEntity<List<AppraisalConfigResponseDTO>> listCycles(
            @RequestParam("year") Integer year
    ) {
        return ResponseEntity.ok(configService.listByYear(year));
    }

    /* ================= LIST ACTIVE CYCLES (for assignment) ================= */

    @RequiresPermission(
            module = AppModule.APPRAISAL,
            action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL}
    )
    @GetMapping("/cycles/active")
    public ResponseEntity<List<AppraisalConfigResponseDTO>> listActiveCycles() {
        return ResponseEntity.ok(configService.listActive());
    }

    /* ================= CREATE ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.CREATE)
    @PostMapping
    public ResponseEntity<AppraisalConfigResponseDTO> create(
            @Valid @RequestBody AppraisalConfigRequestDTO dto
    ) {
        return ResponseEntity.ok(configService.create(dto));
    }

    /* ================= UPDATE ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<AppraisalConfigResponseDTO> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody AppraisalConfigRequestDTO dto
    ) {
        return ResponseEntity.ok(configService.update(id, dto));
    }

    /* ================= ACTIVATE ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PutMapping("/{id}/activate")
    public ResponseEntity<AppraisalConfigResponseDTO> activate(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(configService.activate(id));
    }

    /* ================= CLOSE (NEW - REQUIRED FOR ACTIVE) ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PutMapping("/{id}/close")
    public ResponseEntity<?> close(
            @PathVariable("id") Long id
    ) {
        try {
            return ResponseEntity.ok(configService.close(id));
        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(409)
                    .body(Map.of("error", "Conflict", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Bad Request", "message", e.getMessage()));
        }
    }

    /* ================= SAVE AND ACTIVATE ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PostMapping("/save-and-activate")
    public ResponseEntity<?> saveAndActivate(
            @Valid @RequestBody AppraisalConfigRequestDTO dto
    ) {
        try {
            AppraisalConfigResponseDTO response = configService.saveAndActivate(dto);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(409)
                    .body(Map.of("error", "Conflict", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Bad Request", "message", e.getMessage()));
        }
    }

    /* ================= DELETE ================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id
    ) {
        configService.delete(id);
        return ResponseEntity.noContent().build();
    }
}