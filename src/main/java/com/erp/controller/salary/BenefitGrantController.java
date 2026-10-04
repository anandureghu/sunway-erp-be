package com.erp.controller.salary;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.salary.BenefitGrantDTO;
import com.erp.dto.salary.BenefitGrantRequestDTO;
import com.erp.service.salary.BenefitGrantService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HR Policies → Benefits: one-off benefit grants (annual ticket, bonus,
 * reimbursement) paid through payroll. Viewing needs HR_SETTINGS view;
 * granting or removing needs HR_SETTINGS / EDIT.
 */
@RestController
@RequestMapping("/api/hr/benefit-grants")
@RequiredArgsConstructor
public class BenefitGrantController {

    private final BenefitGrantService benefitGrantService;

    @GetMapping
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    public ResponseEntity<List<BenefitGrantDTO>> list() {
        return ResponseEntity.ok(benefitGrantService.list());
    }

    /** Annual ticket already granted in the pay month's year (empty body when none). */
    @GetMapping("/annual-ticket-check")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    public ResponseEntity<Map<String, Object>> annualTicketCheck(
            @RequestParam Long employeeId,
            @RequestParam String payMonth) {
        Map<String, Object> body = new HashMap<>();
        benefitGrantService.findAnnualTicketInYear(employeeId, payMonth)
                .ifPresentOrElse(
                        existing -> {
                            body.put("alreadyGranted", true);
                            body.put("existing", existing);
                        },
                        () -> body.put("alreadyGranted", false));
        return ResponseEntity.ok(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<BenefitGrantDTO> create(
            @RequestPart("data") BenefitGrantRequestDTO dto,
            @RequestPart(value = "document", required = false) MultipartFile document) {
        return ResponseEntity.ok(benefitGrantService.create(dto, document));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<BenefitGrantDTO> update(
            @PathVariable Long id,
            @RequestPart("data") BenefitGrantRequestDTO dto,
            @RequestPart(value = "document", required = false) MultipartFile document) {
        return ResponseEntity.ok(benefitGrantService.update(id, dto, document));
    }

    /** Paid benefit closed off by HR — hidden from the benefits page afterwards. */
    @PutMapping("/{id}/complete")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<Void> complete(@PathVariable Long id) {
        benefitGrantService.complete(id);
        return ResponseEntity.noContent().build();
    }

    /** Grant one benefit to a group: grade code, department, one employee or all employees. */
    @PostMapping("/bulk")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<com.erp.dto.salary.BenefitGrantBulkResultDTO> createBulk(
            @RequestBody BenefitGrantRequestDTO dto) {
        return ResponseEntity.ok(benefitGrantService.createBulk(dto));
    }

    /**
     * Opens the supporting document through the API: PDFs / images display inline,
     * other files (e.g. Word) download with a readable name. Never expires.
     */
    @GetMapping("/{id}/document")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        com.erp.service.file.FileStorageService.StoredBlob blob = benefitGrantService.downloadDocument(id);
        String name = benefitGrantService.documentFileName(id);
        boolean inline = blob.contentType().startsWith("image/") || blob.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename=\"" + name + "\"")
                .header(org.springframework.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION)
                .body(blob.bytes());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        benefitGrantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
