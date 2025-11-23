package com.erp.controller.hr;

import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.service.hr.DepartmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    // Get all departments for logged-in user's companies
    @GetMapping
    public List<DepartmentResponseDTO> getDepartments() {
        return departmentService.getDepartmentsForCurrentUser();
    }

    // Create department
    @PostMapping
    public DepartmentResponseDTO createDepartment(@RequestBody CreateDepartmentDTO dto) {
        return departmentService.createDepartment(dto);
    }

    // Get single department
    @GetMapping("/{id}")
    public DepartmentResponseDTO getDepartmentById(@PathVariable("id") Long id) {
        return departmentService.getDepartmentById(id);
    }

    // Delete department
    @DeleteMapping("/{id}")
    public void deleteDepartment(@PathVariable("id") Long id) {
        departmentService.deleteDepartment(id);
    }

    // Get departments by company
    @GetMapping("/company/{companyId}")
    public List<DepartmentResponseDTO> getDepartmentsByCompany(@PathVariable("companyId") Long companyId) {
        return departmentService.getDepartmentsByCompanyId(companyId);
    }
}
