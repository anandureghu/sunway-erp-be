package com.erp.controller;

import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.service.hr.EmployeeService;
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

    // CREATE EMPLOYEE (Normal or Admin)
    @PostMapping
    public EmployeeResponseDTO createEmployee(@RequestBody CreateEmployeeDTO dto) {
        return employeeService.createEmployee(dto);
    }

    // GET EMPLOYEE BY ID
    @GetMapping("/{id}")
    public EmployeeResponseDTO getEmployeeById(@PathVariable("id") Long id) {
        return employeeService.getEmployeeById(id);
    }

    // GET EMPLOYEES BY COMPANY
    @GetMapping("/company/{companyId}")
    public List<EmployeeResponseDTO> getEmployeesByCompany(@PathVariable("companyId") Long companyId) {
        return employeeService.getEmployeesByCompany(companyId);
    }

    // GET EMPLOYEES BY DEPARTMENT
    @GetMapping("/department/{departmentId}")
    public List<EmployeeResponseDTO> getEmployeesByDepartment(@PathVariable("departmentId") Long departmentId) {
        return employeeService.getEmployeesByDepartment(departmentId);
    }

    // DELETE EMPLOYEE
    @DeleteMapping("/{id}")
    public void deleteEmployee(@PathVariable("id") Long id) {
        employeeService.deleteEmployee(id);
    }

    // GET COMPANY ADMIN
    @GetMapping("/admin/{companyId}")
    public EmployeeResponseDTO getCompanyAdmin(@PathVariable("companyId") Long companyId) {
        return employeeService.getCompanyAdmin(companyId);
    }

    @PostMapping("/{id}/upload-image")
    public EmployeeResponseDTO uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return employeeService.uploadImage(id, file);
    }
}
