package com.erp.controller.hr;

import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.service.hr.DepartmentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/companies/{companyId}/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentResponseDTO> getDepartments(
            @PathVariable("companyId") Long companyId) {

        return departmentService.getDepartmentsByCompanyId(companyId);
    }

    @PostMapping
    public DepartmentResponseDTO createDepartment(
            @PathVariable("companyId") Long companyId,
            @RequestBody CreateDepartmentDTO dto) {

        return departmentService.createDepartment(companyId, dto);
    }

    @PutMapping("/{id}")
    public DepartmentResponseDTO updateDepartment(
            @PathVariable("companyId") Long companyId,
            @PathVariable("id") Long id,
            @RequestBody CreateDepartmentDTO dto) {

        return departmentService.updateDepartment(companyId, id, dto);
    }

    @GetMapping("/{id}")
    public DepartmentResponseDTO getDepartmentById(
            @PathVariable("companyId") Long companyId,
            @PathVariable("id") Long id) {

        return departmentService.getDepartmentById(companyId, id);
    }

    @DeleteMapping("/{id}")
    public void deleteDepartment(
            @PathVariable("companyId") Long companyId,
            @PathVariable("id") Long id) {

        departmentService.deleteDepartment(companyId, id);
    }
}