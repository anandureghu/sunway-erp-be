package com.erp.controller.hr;

import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateDepartmentDTO;
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

    @GetMapping
    public List<Department> getDepartments() {
        return departmentService.getDepartmentsForCurrentUser();
    }

    @PostMapping
    public Department createDepartment(@RequestBody CreateDepartmentDTO dto) {
        return departmentService.createDepartment(dto);
    }

    @GetMapping("/{id}")
    public Department getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
    }

    @GetMapping("/company/{companyId}")
    public List<Department> getDepartmentsByCompany(@PathVariable("companyId") Long companyId) {
        return departmentService.getDepartmentsByCompanyId(companyId);
    }
}
