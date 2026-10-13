package com.erp.controller.inventory;

import com.erp.domain.inventory.CrmPipelineStatus;
import com.erp.domain.inventory.CrmProspectType;
import com.erp.domain.inventory.CrmRelationshipStage;
import com.erp.dto.inventory.*;
import com.erp.service.inventory.CrmEnquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/crm/enquiries")
@RequiredArgsConstructor
public class CrmEnquiryController {

    private final CrmEnquiryService crmEnquiryService;

    @GetMapping
    public Page<CrmEnquiryResponseDTO> list(
            @RequestParam(required = false) CrmPipelineStatus pipeline,
            @RequestParam(required = false) CrmRelationshipStage stage,
            @RequestParam(required = false) CrmProspectType type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return crmEnquiryService.list(pipeline, stage, type, search, pageable);
    }

    @GetMapping("/{id}")
    public CrmEnquiryResponseDTO get(@PathVariable Long id) {
        return crmEnquiryService.get(id);
    }

    @PostMapping
    public ResponseEntity<CrmEnquiryResponseDTO> create(@RequestBody CrmEnquiryCreateDTO dto) {
        return ResponseEntity.ok(crmEnquiryService.create(dto));
    }

    @PutMapping("/{id}")
    public CrmEnquiryResponseDTO update(@PathVariable Long id, @RequestBody CrmEnquiryUpdateDTO dto) {
        return crmEnquiryService.update(id, dto);
    }

    @PostMapping("/{id}/notes")
    public CrmEnquiryResponseDTO addNote(
            @PathVariable Long id,
            @RequestBody CrmEnquiryNoteCreateDTO dto
    ) {
        return crmEnquiryService.addNote(id, dto);
    }

    @PostMapping("/{id}/convert")
    public CrmEnquiryResponseDTO convert(
            @PathVariable Long id,
            @RequestBody CrmEnquiryConvertDTO dto
    ) {
        return crmEnquiryService.convert(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        crmEnquiryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
