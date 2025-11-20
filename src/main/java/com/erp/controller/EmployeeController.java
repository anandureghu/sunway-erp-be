package com.erp.controller;

import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.service.EmployeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public EmployeeResponseDTO createEmployee(@RequestBody CreateEmployeeDTO dto) {
        return employeeService.createEmployee(dto);
    }

    @GetMapping("/{id}")
    public EmployeeResponseDTO getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

    @GetMapping("/company/{companyId}")
    public List<EmployeeResponseDTO> getByCompany(@PathVariable Long companyId) {
        return employeeService.getEmployeesByCompany(companyId);
    }

    @GetMapping("/department/{departmentId}")
    public List<EmployeeResponseDTO> getByDepartment(@PathVariable Long departmentId) {
        return employeeService.getEmployeesByDepartment(departmentId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }

    @GetMapping("/admin/{companyId}")
    public EmployeeResponseDTO getCompanyAdmin(@PathVariable Long companyId) {
        return employeeService.getCompanyAdmin(companyId);
    }
}
