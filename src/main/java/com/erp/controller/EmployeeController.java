package com.erp.controller;

import com.erp.domain.User;
import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.EmployeeService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuthContext authContext;
    private final UserRepository userRepository;

    public EmployeeController(
            EmployeeService employeeService,
            AuthContext authContext,
            UserRepository userRepository) {
        this.employeeService = employeeService;
        this.authContext = authContext;
        this.userRepository = userRepository;
    }

    // ======================================================
    // CREATE EMPLOYEE
    // Requires CREATE — only admins/HR can create employees
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody CreateEmployeeDTO dto) {

        EmployeeResponseDTO response = employeeService.createEmployee(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ======================================================
    // GET COMPANY ADMIN
    // No permission guard — internal utility endpoint
    // ======================================================
    @GetMapping("/admin/{companyId}")
    public ResponseEntity<EmployeeResponseDTO> getCompanyAdmin(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getCompanyAdmin(companyId));
    }

    // ======================================================
    // GET EMPLOYEES PAGINATED
    // VIEW_OWN allows user to see employee list (their company)
    // VIEW_ALL also allowed
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/page")
    public ResponseEntity<PageResponse<EmployeeResponseDTO>> getEmployeesPaginated(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return ResponseEntity.ok(employeeService.getEmployees(page, size));
    }

    // ======================================================
    // SYNC ALL LEAVE BALANCES
    // Requires EDIT on LEAVES — admin/HR operation
    // ======================================================
    @HrPermission(module = HrModule.LEAVES, action = {HrAction.EDIT})
    @PostMapping("/sync-all-leave-balances")
    public ResponseEntity<Void> syncAllLeaveBalances() {
        User authUser = getAuthUser();
        employeeService.syncAllEmployeeLeaveBalances(authUser.getCompany().getId());
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // GET ALL EMPLOYEES (CURRENT COMPANY)
    // VIEW_OWN allows user to see the employee list
    // VIEW_ALL also allowed
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployees() {
        return ResponseEntity.ok(employeeService.getEmployees());
    }

    // ======================================================
    // GET EMPLOYEE BY ID
    // VIEW_OWN — user can view their own profile
    // VIEW_ALL — admin/HR can view any profile
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // EDIT — user can edit their own profile if granted
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable("id") Long id,
            @RequestBody UpdateEmployeeDTO dto) {

        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // VIEW_ALL only — seeing all employees of a company
    // is an admin/HR level operation
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_ALL})
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByCompany(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getEmployeesByCompany(companyId));
    }

    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // VIEW_ALL only — department-level listing is admin/HR
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_ALL})
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByDepartment(
            @PathVariable("departmentId") Long departmentId) {

        return ResponseEntity.ok(employeeService.getEmployeesByDepartment(departmentId));
    }

    // ======================================================
    // DELETE EMPLOYEE
    // DELETE only — hard delete is admin only
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable("id") Long id) {

        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // ======================================================
    // UPLOAD PROFILE IMAGE
    // EDIT — same permission as updating profile
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.EDIT})
    @PostMapping("/{id}/upload-image")
    public ResponseEntity<EmployeeResponseDTO> uploadImage(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(
                employeeService.uploadProfileImage(id, file)
        );
    }

    // ======================================================
    // HELPER METHOD
    // ======================================================
    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("Unauthorized");
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}