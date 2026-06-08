package com.erp.controller.security;

import com.erp.domain.CompanyLeavePolicy;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeaveBalance;
import com.erp.domain.hr.Company;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.LeavePolicyService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    private final CompanyRepository companyRepository;
    private final AuthContext authContext;
    private final LeavePolicyService leavePolicyService;

    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.VIEW_ALL})
    @GetMapping("/diagnose/{employeeId}")
    public ResponseEntity<?> diagnoseEmployee(@PathVariable Long employeeId) {
        try {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            assertEmployeeInCallerTenant(employee);

            Map<String, Object> diagnosis = new HashMap<>();
            diagnosis.put("employeeId", employeeId);
            diagnosis.put("employeeName", fullName(employee));
            diagnosis.put("employeeRole", resolveEmployeeRole(employee));
            diagnosis.put("company", employee.getCompany() != null ? employee.getCompany().getCompanyName() : null);

            String effectiveRole = resolveEmployeeRole(employee);

            if (effectiveRole == null || effectiveRole.isBlank()) {
                diagnosis.put("roleStatus", "MISSING - Employee has no role set");
                diagnosis.put("availablePolicies", List.of());
                diagnosis.put("leaveBalances", List.of());
                diagnosis.put("recommendation", "Update employee role before leave balance initialization");
                return ResponseEntity.ok(diagnosis);
            }

            List<CompanyLeavePolicy> policies = policyRepository
                    .findByCompanyOrderByIdDesc(employee.getCompany())
                    .stream()
                    .filter(p -> same(p.getRole(), effectiveRole))
                    .toList();

            diagnosis.put("roleStatus", "SET");
            diagnosis.put("effectiveRole", effectiveRole);
            diagnosis.put("policiesCount", policies.size());
            diagnosis.put("availablePolicies", policies.stream().map(p -> Map.of(
                    "id", p.getId(),
                    "leaveType", p.getLeaveType(),
                    "defaultDays", p.getDefaultDays(),
                    "paid", p.getPaid(),
                    "genderRestricted", p.getGenderRestricted(),
                    "allowedGender", p.getAllowedGender()
            )).toList());

            List<Map<String, Object>> balances = new ArrayList<>();
            for (String leaveType : policies.stream()
                    .map(CompanyLeavePolicy::getLeaveType)
                    .distinct()
                    .collect(Collectors.toList())) {

                Optional<EmployeeLeaveBalance> balance = balanceRepository
                        .findByEmployeeAndLeaveType(employee, normalize(leaveType));

                if (balance.isEmpty()) {
                    balance = balanceRepository.findByEmployeeAndLeaveType(employee, leaveType);
                }

                if (balance.isPresent()) {
                    EmployeeLeaveBalance b = balance.get();
                    balances.add(Map.of(
                            "leaveType", leaveType,
                            "totalLeaves", b.getTotalLeaves(),
                            "remainingLeaves", b.getRemainingLeaves(),
                            "usedLeaves", b.getTotalLeaves() - b.getRemainingLeaves(),
                            "status", "EXISTS"
                    ));
                } else {
                    balances.add(Map.of(
                            "leaveType", leaveType,
                            "status", "MISSING"
                    ));
                }
            }

            diagnosis.put("leaveBalances", balances);
            diagnosis.put("recommendation", balances.stream()
                    .anyMatch(b -> String.valueOf(b.get("status")).contains("MISSING"))
                    ? "Run POST /api/admin/leaves/initialize/{employeeId}"
                    : "All leave balances are initialized");

            return ResponseEntity.ok(diagnosis);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.EDIT})
    @PostMapping("/initialize/{employeeId}")
    public ResponseEntity<?> initializeLeaveBalances(@PathVariable Long employeeId) {
        try {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            assertEmployeeInCallerTenant(employee);

            String effectiveRole = resolveEmployeeRole(employee);
            if (effectiveRole == null || effectiveRole.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Employee has no role set. Update employee role first.",
                        "hint", "Use employee update API to set role or companyRole"
                ));
            }

            leavePolicyService.initializeLeaveBalancesForEmployee(employee);

            log.info("Initialized leave balances for employee {}", employeeId);

            return ResponseEntity.ok(Map.of(
                    "message", "Leave balances initialized successfully",
                    "employeeId", employeeId,
                    "employeeName", fullName(employee),
                    "effectiveRole", effectiveRole
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            log.error("Failed to initialize leave balances: {}", e.getMessage(), e);
            return badRequest(e);
        } catch (Exception e) {
            log.error("Failed to initialize leave balances: {}", e.getMessage(), e);
            return serverError(e);
        }
    }

    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.EDIT})
    @PostMapping("/initialize-all")
    public ResponseEntity<?> initializeAllMissingBalances(
            @RequestParam(defaultValue = "false") boolean fixRoles) {
        try {
            Long tenantCompanyId = authContext.getCurrentCompanyId();
            if (tenantCompanyId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Company context required for bulk initialize"
                ));
            }

            List<Employee> allEmployees = employeeRepository.findByCompany_IdOrderByCreatedAtDesc(tenantCompanyId);

            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> report = new ArrayList<>();

            for (Employee employee : allEmployees) {
                Map<String, Object> empReport = new HashMap<>();
                empReport.put("employeeId", employee.getId());
                empReport.put("name", fullName(employee));

                String effectiveRole = resolveEmployeeRole(employee);

                if ((effectiveRole == null || effectiveRole.isBlank()) && fixRoles
                        && employee.getUser() != null
                        && employee.getUser().getRole() != null) {

                    employee.setRole(employee.getUser().getRole().name());
                    employeeRepository.save(employee);
                    effectiveRole = employee.getRole();
                    empReport.put("roleFixed", "SET_FROM_USER_ROLE");
                }

                if (effectiveRole == null || effectiveRole.isBlank()) {
                    empReport.put("status", "SKIPPED_NO_ROLE");
                    report.add(empReport);
                    continue;
                }

                try {
                    leavePolicyService.initializeLeaveBalancesForEmployee(employee);
                    empReport.put("status", "INITIALIZED");
                    empReport.put("effectiveRole", effectiveRole);
                } catch (Exception e) {
                    empReport.put("status", "FAILED");
                    empReport.put("details", e.getMessage());
                }

                report.add(empReport);
            }

            result.put("totalEmployees", allEmployees.size());
            result.put("processed", report.size());
            result.put("report", report);

            log.info("Bulk initialization complete: {} employees processed", report.size());

            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.EDIT})
    @PostMapping("/fix-roles")
    public ResponseEntity<?> fixMissingRoles() {
        try {
            Long tenantCompanyId = authContext.getCurrentCompanyId();
            if (tenantCompanyId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Company context required"
                ));
            }

            List<Employee> allEmployees = employeeRepository.findByCompany_IdOrderByCreatedAtDesc(tenantCompanyId);
            List<Map<String, Object>> report = new ArrayList<>();

            for (Employee employee : allEmployees) {
                if (resolveEmployeeRole(employee) != null && !resolveEmployeeRole(employee).isBlank()) {
                    continue;
                }

                if (employee.getUser() == null || employee.getUser().getRole() == null) {
                    report.add(Map.of(
                            "employeeId", employee.getId(),
                            "name", fullName(employee),
                            "status", "SKIPPED_NO_USER_ROLE"
                    ));
                    continue;
                }

                employee.setRole(employee.getUser().getRole().name());
                employeeRepository.save(employee);

                report.add(Map.of(
                        "employeeId", employee.getId(),
                        "name", fullName(employee),
                        "roleSet", employee.getUser().getRole().name(),
                        "status", "FIXED"
                ));
            }

            long fixedCount = report.stream()
                    .filter(r -> "FIXED".equals(String.valueOf(r.get("status"))))
                    .count();

            log.info("Fixed roles for {} employees", fixedCount);

            return ResponseEntity.ok(Map.of(
                    "totalProcessed", report.size(),
                    "fixedCount", fixedCount,
                    "report", report
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.VIEW_ALL})
    @GetMapping("/policies")
    public ResponseEntity<?> getAllPolicies(@RequestParam(required = false) Long companyId) {
        try {
            Long resolvedCompanyId = companyId != null ? companyId : authContext.getCurrentCompanyId();
            assertResolvedCompanyAccess(resolvedCompanyId);

            Company company = companyRepository.findById(resolvedCompanyId)
                    .orElseThrow(() -> new RuntimeException("Company not found"));

            List<CompanyLeavePolicy> policies = policyRepository.findByCompanyOrderByIdDesc(company);

            return ResponseEntity.ok(Map.of(
                    "totalPolicies", policies.size(),
                    "policies", policies.stream().map(p -> Map.of(
                            "id", p.getId(),
                            "company", p.getCompany().getCompanyName(),
                            "role", p.getRole(),
                            "leaveType", p.getLeaveType(),
                            "defaultDays", p.getDefaultDays(),
                            "paid", p.getPaid(),
                            "genderRestricted", p.getGenderRestricted(),
                            "allowedGender", p.getAllowedGender()
                    )).toList()
            ));
        } catch (AccessDeniedException e) {
            return forbidden(e);
        } catch (RuntimeException e) {
            return badRequest(e);
        }
    }

    private String resolveEmployeeRole(Employee employee) {
        if (employee == null) {
            return null;
        }

        if (employee.getCompanyRole() != null && !employee.getCompanyRole().isBlank()) {
            return employee.getCompanyRole().trim();
        }

        if (employee.getRole() != null && !employee.getRole().isBlank()) {
            return employee.getRole().trim();
        }

        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean same(String left, String right) {
        String l = normalize(left);
        String r = normalize(right);
        return l != null && l.equals(r);
    }

    private String fullName(Employee employee) {
        String first = employee.getFirstName() != null ? employee.getFirstName().trim() : "";
        String last = employee.getLastName() != null ? employee.getLastName().trim() : "";
        return (first + " " + last).trim();
    }

    private boolean isSuperAdmin() {
        String role = authContext.getCurrentUserRole();
        return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    private void assertEmployeeInCallerTenant(Employee employee) {
        if (isSuperAdmin()) {
            return;
        }

        Long cid = authContext.getCurrentCompanyId();
        if (employee.getCompany() == null || cid == null || !cid.equals(employee.getCompany().getId())) {
            throw new AccessDeniedException("Access denied for this employee");
        }
    }

    private void assertResolvedCompanyAccess(Long resolvedCompanyId) {
        if (resolvedCompanyId == null) {
            throw new AccessDeniedException("Company context required");
        }

        if (isSuperAdmin()) {
            return;
        }

        Long current = authContext.getCurrentCompanyId();
        if (current == null || !current.equals(resolvedCompanyId)) {
            throw new AccessDeniedException("Access denied for this company");
        }
    }

    private ResponseEntity<Map<String, String>> forbidden(Exception e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    private ResponseEntity<Map<String, String>> badRequest(Exception e) {
        log.warn("Request failed: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private ResponseEntity<Map<String, String>> serverError(Exception e) {
        log.error("Server error: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}