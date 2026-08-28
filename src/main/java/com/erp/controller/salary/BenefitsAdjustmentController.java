package com.erp.controller.salary;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.salary.BenefitsAdjustmentRequestDTO;
import com.erp.dto.salary.BenefitsAdjustmentResultDTO;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.salary.BenefitsAdjustmentService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HR Settings → bulk benefits adjustment. Raising employee benefits is an
 * edit-level HR-settings action, so both endpoints require HR_SETTINGS / EDIT.
 */
@RestController
@RequestMapping("/api/hr/benefits-adjustment")
@RequiredArgsConstructor
public class BenefitsAdjustmentController {

    private final BenefitsAdjustmentService benefitsAdjustmentService;
    private final JobCodeRepository jobCodeRepository;
    private final AuthContext authContext;

    /** Distinct salary-grade codes for the current company, for the grade-code picker. */
    @GetMapping("/grade-codes")
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<List<String>> gradeCodes() {
        Long companyId = authContext.getCurrentCompanyId();
        List<String> grades = jobCodeRepository.findByCompany_IdAndActiveTrue(companyId).stream()
                .map(jc -> jc.getSalaryGrade())
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(grades);
    }

    @PostMapping
    @RequiresPermission(module = AppModule.HR_SETTINGS, action = {AppAction.EDIT})
    public ResponseEntity<BenefitsAdjustmentResultDTO> adjust(
            @RequestBody BenefitsAdjustmentRequestDTO request) {
        return ResponseEntity.ok(benefitsAdjustmentService.adjust(request));
    }
}
