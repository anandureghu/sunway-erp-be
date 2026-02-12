package com.erp.controller;

import com.erp.domain.User;
import com.erp.dto.common.PageResponse;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.EmployeeService;
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
    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody CreateEmployeeDTO dto) {

        EmployeeResponseDTO response = employeeService.createEmployee(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ======================================================
    // GET COMPANY ADMIN
    // ======================================================
    @GetMapping("/admin/{companyId}")
    public ResponseEntity<EmployeeResponseDTO> getCompanyAdmin(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getCompanyAdmin(companyId));
    }

    // ======================================================
    // GET EMPLOYEES (PAGINATED)
    // ======================================================
    @GetMapping("/page")
    public ResponseEntity<PageResponse<EmployeeResponseDTO>> getEmployees(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return ResponseEntity.ok(employeeService.getEmployees(page, size));
    }

    // ======================================================
    // SYNC ALL LEAVE BALANCES
    // ======================================================
    @PostMapping("/sync-all-leave-balances")
    public ResponseEntity<Void> syncAllLeaveBalances() {
        User authUser = getAuthUser();
        employeeService.syncAllEmployeeLeaveBalances(authUser.getCompany().getId());
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // GET EMPLOYEES (CURRENT COMPANY)
    // ======================================================
    @GetMapping
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployees() {
        return ResponseEntity.ok(employeeService.getEmployees());
    }

    // ======================================================
    // GET EMPLOYEE BY ID
    // ======================================================
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable("id") Long id,
            @RequestBody UpdateEmployeeDTO dto) {

        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // ======================================================
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByCompany(
            @PathVariable("companyId") Long companyId) {

        return ResponseEntity.ok(employeeService.getEmployeesByCompany(companyId));
    }

    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // ======================================================
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<EmployeeResponseDTO>> getEmployeesByDepartment(
            @PathVariable("departmentId") Long departmentId) {

        return ResponseEntity.ok(employeeService.getEmployeesByDepartment(departmentId));
    }

    // ======================================================
    // DELETE EMPLOYEE
    // ======================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable("id") Long id) {

        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // ======================================================
    // UPLOAD PROFILE IMAGE (AZURE FIXED)
    // ======================================================
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
