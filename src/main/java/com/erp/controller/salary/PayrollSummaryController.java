package com.erp.controller.salary;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.salary.PayrollSummaryRowDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.salary.PayrollService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Company-wide payroll summary report for HR — all payroll runs, optionally bounded
 * by pay date, ready to be grouped by department on the client. Gated by the payroll
 * view-all grant so only users who may see company payroll can read it.
 */
@RestController
@RequestMapping("/api/hr/payroll-summary")
public class PayrollSummaryController {

    private final PayrollService payrollService;
    private final AuthContext authContext;

    public PayrollSummaryController(PayrollService payrollService, AuthContext authContext) {
        this.payrollService = payrollService;
        this.authContext = authContext;
    }

    @RequiresPermission(module = AppModule.PAYROLL, action = {AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<PayrollSummaryRowDTO>> get(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        return ResponseEntity.ok(
                payrollService.getCompanyPayrollSummary(companyId, from, to));
    }
}
