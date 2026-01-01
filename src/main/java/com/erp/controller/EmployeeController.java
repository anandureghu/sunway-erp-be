package com.erp.controller;

import com.erp.dto.common.PageResponse;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // ======================================================
    // CREATE EMPLOYEE
    // ======================================================
    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @RequestBody CreateEmployeeDTO dto) {

        return ResponseEntity.ok(employeeService.createEmployee(dto));
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
    public PageResponse<EmployeeResponseDTO> getEmployees(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return employeeService.getEmployees(page, size);
    }

    // ======================================================
    // GET EMPLOYEES (CURRENT COMPANY)
    // ======================================================
    @GetMapping
    public List<EmployeeResponseDTO> getEmployees() {
        return employeeService.getEmployees();
    }

    // ======================================================
    // GET EMPLOYEE BY ID
    // ======================================================
    @GetMapping("/{id}")
    public EmployeeResponseDTO getEmployeeById(
            @PathVariable("id") Long id) {

        return employeeService.getEmployeeById(id);
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable("id") Long id,
            @RequestBody UpdateEmployeeDTO dto
    ) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // ======================================================
    @GetMapping("/company/{companyId}")
    public List<EmployeeResponseDTO> getEmployeesByCompany(
            @PathVariable("companyId") Long companyId) {

        return employeeService.getEmployeesByCompany(companyId);
    }

    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // ======================================================
    @GetMapping("/department/{departmentId}")
    public List<EmployeeResponseDTO> getEmployeesByDepartment(
            @PathVariable("departmentId") Long departmentId) {

        return employeeService.getEmployeesByDepartment(departmentId);
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

    @PostMapping("/{id}/upload-image")
    public EmployeeResponseDTO uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return employeeService.uploadImage(id, file);
    }
}
