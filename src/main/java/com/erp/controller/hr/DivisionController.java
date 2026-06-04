package com.erp.controller.hr;

import com.erp.dto.hr.CreateDivisionDTO;
import com.erp.dto.hr.DivisionResponseDTO;
import com.erp.service.hr.DivisionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/divisions")
public class DivisionController {
    private final DivisionService divisionService;

    public DivisionController(DivisionService divisionService) {
        this.divisionService = divisionService;
    }

    // Get all departments for logged-in user's companies
    @GetMapping
    public List<DivisionResponseDTO> getDepartments() {
        return divisionService.getDivisionsForCurrentUser();
    }

    // Create department
    @PostMapping
    public DivisionResponseDTO createDivision(@RequestBody CreateDivisionDTO dto) {
        return divisionService.createDivision(dto);
    }

    // Get single department
    @GetMapping("/{id}")
    public DivisionResponseDTO getDepartmentById(@PathVariable("id") Long id) {
        return divisionService.getDivisionById(id);
    }

    // Update division
    @PutMapping("/{id}")
    public DivisionResponseDTO updateDivision(@PathVariable("id") Long id,
                                              @RequestBody CreateDivisionDTO dto) {
        return divisionService.updateDivision(id, dto);
    }

    // Delete department
    @DeleteMapping("/{id}")
    public void deleteDivision(@PathVariable("id") Long id) {
        divisionService.deleteDivision(id);
    }

    // Get departments by company
    @GetMapping("/company/{companyId}")
    public List<DivisionResponseDTO> getDivisionsByCompany(@PathVariable("companyId") Long companyId) {
        return divisionService.getDivisionsByCompanyId(companyId);
    }
}
