package com.erp.controller.hr;

import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentCsvImportResultDTO;
import com.erp.dto.hr.DepartmentCsvPreviewDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.service.hr.DepartmentCsvImportService;
import com.erp.service.hr.DepartmentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentCsvImportService csvImportService;

    public DepartmentController(DepartmentService departmentService, DepartmentCsvImportService csvImportService) {
        this.departmentService = departmentService;
        this.csvImportService = csvImportService;
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

    @PostMapping(value = "/import-csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DepartmentCsvPreviewDTO previewImportCsv(
            @PathVariable("companyId") Long companyId,
            @RequestPart("file") MultipartFile file) {
        return csvImportService.preview(file);
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DepartmentCsvImportResultDTO importCsv(
            @PathVariable("companyId") Long companyId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mapping) {
        return csvImportService.importCsv(file, mapping);
    }
}