package com.erp.controller;

import com.erp.domain.User;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.EmployeeService;
import com.erp.service.security.annotation.RequiresPermission;
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
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody CreateEmployeeDTO dto) {

        EmployeeResponseDTO response = employeeService.createEmployee(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ======================================================
    // GET COMPANY ADMIN
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_ALL})
    @GetMapping("/admin/{companyId}")
    public ResponseEntity<EmployeeResponseDTO> getCompanyAdmin(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getCompanyAdmin(companyId));
    }

    // ======================================================
    // GET EMPLOYEES PAGINATED
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/page")
    public ResponseEntity<PageResponse<EmployeeResponseDTO>> getEmployeesPaginated(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return ResponseEntity.ok(employeeService.getEmployees(page, size));
    }

    // ======================================================
    // SYNC ALL LEAVE BALANCES
    // ======================================================
    @RequiresPermission(module = AppModule.LEAVES, action = {AppAction.EDIT})
    @PostMapping("/sync-all-leave-balances")
    public ResponseEntity<Void> syncAllLeaveBalances() {
        User authUser = getAuthUser();
        employeeService.syncAllEmployeeLeaveBalances(authUser.getCompany().getId());
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // GET ALL EMPLOYEES (CURRENT COMPANY)
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployees() {
        return ResponseEntity.ok(employeeService.getEmployees());
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_ALL})
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByCompany(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getEmployeesByCompany(companyId));
    }

    // ======================================================
    // GET MANAGERS BY COMPANY  ✅ FIXED POSITION
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_ALL})
    @GetMapping("/managers")
    public ResponseEntity<List<EmployeeResponseDTO>> getManagersByCompany(
            @RequestParam("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getManagersByCompany(companyId));
    }
    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_ALL})
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByDepartment(
            @PathVariable("departmentId") Long departmentId) {

        return ResponseEntity.ok(employeeService.getEmployeesByDepartment(departmentId));
    }

    // ======================================================
    // GET EMPLOYEE BY ID  ⚠️ KEEP BELOW SPECIFIC ROUTES
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable("id") Long id,
            @RequestBody UpdateEmployeeDTO dto) {

        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    // ======================================================
    // DELETE EMPLOYEE
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable("id") Long id) {

        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // ======================================================
    // UPLOAD PROFILE IMAGE
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
    @PostMapping("/{id}/upload-image")
    public ResponseEntity<EmployeeResponseDTO> uploadImage(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(
                employeeService.uploadProfileImage(id, file)
        );
    }

    // ======================================================
    // HELPER
    // ======================================================
    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("Unauthorized");
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}