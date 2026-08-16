package com.erp.controller.hr;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.ExitInterviewSummaryDTO;
import com.erp.service.hr.ExitInterviewService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company-wide exit / termination interview list for HR Reports. Gated by the
 * EMPLOYEE_PROFILE view-all grant so managers/HR can review submitted forms.
 */
@RestController
@RequestMapping("/api/hr/exit-interviews")
public class ExitInterviewReportController {

    private final ExitInterviewService service;

    public ExitInterviewReportController(ExitInterviewService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<ExitInterviewSummaryDTO>> list() {
        return ResponseEntity.ok(service.listForCompany());
    }
}
