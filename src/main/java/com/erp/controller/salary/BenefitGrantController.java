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

    @DeleteMapping("/{id}")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        benefitGrantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
