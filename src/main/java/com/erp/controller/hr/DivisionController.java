package com.erp.controller.hr;

import com.erp.dto.hr.CreateDivisionDTO;
import com.erp.dto.hr.DivisionCsvImportResultDTO;
import com.erp.dto.hr.DivisionCsvPreviewDTO;
import com.erp.dto.hr.DivisionResponseDTO;
import com.erp.service.hr.DivisionCsvImportService;
import com.erp.service.hr.DivisionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/divisions")
public class DivisionController {
    private final DivisionService divisionService;
    private final DivisionCsvImportService csvImportService;

    public DivisionController(DivisionService divisionService, DivisionCsvImportService csvImportService) {
        this.divisionService = divisionService;
        this.csvImportService = csvImportService;
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

    // Get divisions by company
    @GetMapping("/company/{companyId}")
    public List<DivisionResponseDTO> getDivisionsByCompany(@PathVariable("companyId") Long companyId) {
        return divisionService.getDivisionsByCompanyId(companyId);
    }

    // Get divisions under a department (subcategories)
    @GetMapping("/department/{departmentId}")
    public List<DivisionResponseDTO> getDivisionsByDepartment(
            @PathVariable("departmentId") Long departmentId) {
        return divisionService.getDivisionsByDepartmentId(departmentId);
    }

    @PostMapping(value = "/import-csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DivisionCsvPreviewDTO previewImportCsv(@RequestPart("file") MultipartFile file) {
        return csvImportService.preview(file);
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DivisionCsvImportResultDTO importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping) {
        return csvImportService.importCsv(file, mapping);
    }
}
