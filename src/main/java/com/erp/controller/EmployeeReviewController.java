package com.erp.controller;

import com.erp.dto.review.EmployeeReviewRequestDTO;
import com.erp.dto.review.EmployeeReviewResponseDTO;
import com.erp.service.EmployeeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/review")
@RequiredArgsConstructor
public class EmployeeReviewController {

    private final EmployeeReviewService reviewService;

    @PostMapping
    public ResponseEntity<EmployeeReviewResponseDTO> create(@RequestBody EmployeeReviewRequestDTO dto) {
        return ResponseEntity.ok(reviewService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeReviewResponseDTO> update(
            @PathVariable Long id, @RequestBody EmployeeReviewRequestDTO dto) {
        return ResponseEntity.ok(reviewService.update(id, dto));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<List<EmployeeReviewResponseDTO>> get(@PathVariable Long employeeId) {
        return ResponseEntity.ok(reviewService.getByEmployee(employeeId));
    }
}
