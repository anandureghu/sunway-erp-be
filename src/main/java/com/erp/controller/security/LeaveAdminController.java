package com.erp.controller.security;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.service.LeavePolicyService;
import com.erp.service.security.annotation.HrPermission;
import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveAdminController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveBalanceRepository balanceRepository;
    private final CompanyLeavePolicyRepository policyRepository;
    private final LeavePolicyService leavePolicyService;

    /* ═══════════════════════════════════════════════
       DIAGNOSTIC: Check leave setup for an employee
    ═══════════════════════════════════════════════ */

    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_ALL})
    @GetMapping("/diagnose/{employeeId}")
    public ResponseEntity<?> diagnoseEmployee(@PathVariable Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Map<String, Object> diagnosis = new HashMap<>();
        diagnosis.put("employeeId", employeeId);
        diagnosis.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
        diagnosis.put("employeeRole", employee.getRole());
        diagnosis.put("company", employee.getCompany() != null ? employee.getCompany().getCompanyName() : null);

        // ✅ Check if role is set
        if (employee.getRole() == null || employee.getRole().isBlank()) {
            diagnosis.put("roleStatus", "❌ MISSING - Employee has no role set!");
            diagnosis.put("availablePolicies", List.of());
            diagnosis.put("leaveBalances", List.of());
            return ResponseEntity.ok(diagnosis);
        }

        // ✅ Get available policies for this employee's role
        List<CompanyLeavePolicy> policies = policyRepository
                .findByCompanyAndRole(employee.getCompany(), employee.getRole());

        diagnosis.put("roleStatus", "✅ Set to: " + employee.getRole());
        diagnosis.put("policiesCount", policies.size());
        diagnosis.put("availablePolicies", policies.stream().map(p -> Map.of(
                "id", p.getId(),
                "leaveType", p.getLeaveType(),
                "defaultDays", p.getDefaultDays(),
                "paid", p.getPaid(),
                "genderRestricted", p.getGenderRestricted(),
                "allowedGender", p.getAllowedGender()
        )).toList());

        // ✅ Get existing leave balances
        List<Map<String, Object>> balances = new ArrayList<>();
        for (String leaveType : policies.stream()
                .map(CompanyLeavePolicy::getLeaveType)
                .distinct()
                .collect(Collectors.toList())) {

            Optional<EmployeeLeaveBalance> balance = balanceRepository
                    .findByEmployeeAndLeaveType(employee, leaveType);

            if (balance.isPresent()) {
                EmployeeLeaveBalance b = balance.get();
                balances.add(Map.of(
                        "leaveType", leaveType,
                        "totalLeaves", b.getTotalLeaves(),
                        "remainingLeaves", b.getRemainingLeaves(),
                        "usedLeaves", b.getTotalLeaves() - b.getRemainingLeaves(),
                        "status", "✅ EXISTS"
                ));
            } else {
                balances.add(Map.of(
                        "leaveType", leaveType,
                        "status", "❌ MISSING - Need to initialize"
                ));
            }
        }

        diagnosis.put("leaveBalances", balances);
        diagnosis.put("recommendation", balances.stream()
                .anyMatch(b -> b.get("status").toString().contains("MISSING"))
                ? "⚠️  Run POST /api/admin/leaves/initialize/{employeeId}"
                : "✅ All leave balances are initialized");

        return ResponseEntity.ok(diagnosis);
    }

    /* ═══════════════════════════════════════════════
       INITIALIZE: Create missing leave balances
    ═══════════════════════════════════════════════ */

    @HrPermission(module = HrModule.LEAVES, action = {HrAction.EDIT})
    @PostMapping("/initialize/{employeeId}")
    public ResponseEntity<?> initializeLeaveBalances(@PathVariable Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // ✅ Check if role is set
        if (employee.getRole() == null || employee.getRole().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Employee has no role set. Update employee.role first.",
                    "hint", "Use PUT /api/employees/{id} to set the role"
            ));
        }

        try {
            leavePolicyService.initializeLeaveBalancesForEmployee(employee);

            log.info("✅ Initialized leave balances for employee {}", employeeId);

            return ResponseEntity.ok(Map.of(
                    "message", "Leave balances initialized successfully",
                    "employeeId", employeeId,
                    "employeeName", employee.getFirstName() + " " + employee.getLastName()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to initialize leave balances: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to initialize leave balances",
                    "details", e.getMessage()
            ));
        }
    }

    /* ═══════════════════════════════════════════════
       BULK FIX: Initialize for all employees missing balances
    ═══════════════════════════════════════════════ */

    @HrPermission(module = HrModule.LEAVES, action = {HrAction.EDIT})
    @PostMapping("/initialize-all")
    public ResponseEntity<?> initializeAllMissingBalances(
            @RequestParam(defaultValue = "false") boolean fixRoles) {

        List<Employee> allEmployees = employeeRepository.findAll();
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> report = new ArrayList<>();

        for (Employee employee : allEmployees) {
            Map<String, Object> empReport = new HashMap<>();
            empReport.put("employeeId", employee.getId());
            empReport.put("name", employee.getFirstName() + " " + employee.getLastName());

            // ✅ Check if role is set
            if (employee.getRole() == null || employee.getRole().isBlank()) {
                if (fixRoles && employee.getUser() != null && employee.getUser().getRole() != null) {
                    // Auto-fix: set role from user
                    employee.setRole(employee.getUser().getRole().name());
                    employeeRepository.save(employee);
                    empReport.put("roleFixed", "✅ Set from user.role");
                } else {
                    empReport.put("status", "⏭️  SKIPPED - No role to set");
                    report.add(empReport);
                    continue;
                }
            }

            try {
                leavePolicyService.initializeLeaveBalancesForEmployee(employee);
                empReport.put("status", "✅ Initialized");
            } catch (Exception e) {
                empReport.put("status", "❌ Failed: " + e.getMessage());
            }

            report.add(empReport);
        }

        result.put("totalEmployees", allEmployees.size());
        result.put("processed", report.size());
        result.put("report", report);

        log.info("✅ Bulk initialization complete: {} employees processed", report.size());

        return ResponseEntity.ok(result);
    }

    /* ═══════════════════════════════════════════════
       FIX ROLES: Sync employee.role from user.role
    ═══════════════════════════════════════════════ */

    @HrPermission(module = HrModule.LEAVES, action = {HrAction.EDIT})
    @PostMapping("/fix-roles")
    public ResponseEntity<?> fixMissingRoles() {

        List<Employee> allEmployees = employeeRepository.findAll();
        List<Map<String, Object>> report = new ArrayList<>();

        for (Employee employee : allEmployees) {
            // Skip if role already set
            if (employee.getRole() != null && !employee.getRole().isBlank()) {
                continue;
            }

            // Skip if no user linked
            if (employee.getUser() == null || employee.getUser().getRole() == null) {
                report.add(Map.of(
                        "employeeId", employee.getId(),
                        "name", employee.getFirstName() + " " + employee.getLastName(),
                        "status", "⏭️  SKIPPED - No user or user role"
                ));
                continue;
            }

            // ✅ Fix: Set role from user
            employee.setRole(employee.getUser().getRole().name());
            employeeRepository.save(employee);

            report.add(Map.of(
                    "employeeId", employee.getId(),
                    "name", employee.getFirstName() + " " + employee.getLastName(),
                    "roleSet", employee.getUser().getRole().name(),
                    "status", "✅ Fixed"
            ));
        }

        log.info("✅ Fixed roles for {} employees", report.stream()
                .filter(r -> r.get("status").toString().contains("Fixed")).count());

        return ResponseEntity.ok(Map.of(
                "totalProcessed", report.size(),
                "report", report
        ));
    }

    /* ═══════════════════════════════════════════════
       GET ALL LEAVE POLICIES
    ═══════════════════════════════════════════════ */

    @HrPermission(module = HrModule.LEAVES, action = {HrAction.VIEW_ALL})
    @GetMapping("/policies")
    public ResponseEntity<?> getAllPolicies(
            @RequestParam(required = false) Long companyId) {

        List<CompanyLeavePolicy> policies;

        if (companyId != null) {
            policies = policyRepository.findAll().stream()
                    .filter(p -> p.getCompany().getId().equals(companyId))
                    .toList();
        } else {
            policies = policyRepository.findAll();
        }

        return ResponseEntity.ok(Map.of(
                "totalPolicies", policies.size(),
                "policies", policies.stream().map(p -> Map.of(
                        "id", p.getId(),
                        "company", p.getCompany().getCompanyName(),
                        "role", p.getRole(),
                        "leaveType", p.getLeaveType(),
                        "defaultDays", p.getDefaultDays(),
                        "paid", p.getPaid()
                )).toList()
        ));
    }
}
