package com.erp.controller.hr;

import com.erp.dto.hr.report.EmployeeReportRowDTO;
import com.erp.dto.hr.report.EmployeeSummaryReportDTO;
import com.erp.service.hr.EmployeeReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HR Reports → Employee register and single-employee summary. Access (HR_REPORTS or
 * HRR_WORKFORCE) and salary visibility (SALARY / PAYROLL view-all) are enforced in
 * {@link EmployeeReportService}.
 */
@RestController
@RequestMapping("/api/hr/reports/employees")
@RequiredArgsConstructor
public class EmployeeReportController {

    private final EmployeeReportService reportService;

    /** Every non-archived employee in the caller's company. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> register() {
        List<EmployeeReportRowDTO> rows = reportService.register();
        return ResponseEntity.ok(Map.of(
                "rows", rows,
                "salaryVisible", reportService.salaryVisible()));
    }

    /** One employee's full summary sheet. */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeSummaryReportDTO> summary(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reportService.summary(id));
    }
}
