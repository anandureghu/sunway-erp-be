package com.erp.service;


import com.erp.domain.Employee;
import com.erp.domain.EmployeeReview;
import com.erp.dto.review.EmployeeReviewRequestDTO;
import com.erp.dto.review.EmployeeReviewResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.EmployeeReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeReviewService {

    private final EmployeeReviewRepository reviewRepo;
    private final EmployeeRepository employeeRepo;

    public EmployeeReviewResponseDTO create(EmployeeReviewRequestDTO dto) {

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeReview review = EmployeeReview.builder()
                .employee(employee)
                .month(dto.getMonth())
                .year(dto.getYear())
                .kpi1(dto.getKpi1())
                .kpi2(dto.getKpi2())
                .kpi3(dto.getKpi3())
                .kpi4(dto.getKpi4())
                .kpi5(dto.getKpi5())
                .review1(dto.getReview1())
                .review2(dto.getReview2())
                .review3(dto.getReview3())
                .review4(dto.getReview4())
                .review5(dto.getReview5())
                .jobCode(dto.getJobCode())
                .employeeComments(dto.getEmployeeComments())
                .managerComments(dto.getManagerComments())
                .createdDate(LocalDate.now())
                .build();

        reviewRepo.save(review);
        return toDTO(review);
    }

    public EmployeeReviewResponseDTO update(Long id, EmployeeReviewRequestDTO dto) {

        EmployeeReview review = reviewRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        review.setMonth(dto.getMonth());
        review.setYear(dto.getYear());
        review.setKpi1(dto.getKpi1());
        review.setKpi2(dto.getKpi2());
        review.setKpi3(dto.getKpi3());
        review.setKpi4(dto.getKpi4());
        review.setKpi5(dto.getKpi5());
        review.setReview1(dto.getReview1());
        review.setReview2(dto.getReview2());
        review.setReview3(dto.getReview3());
        review.setReview4(dto.getReview4());
        review.setReview5(dto.getReview5());
        review.setJobCode(dto.getJobCode());
        review.setEmployeeComments(dto.getEmployeeComments());
        review.setManagerComments(dto.getManagerComments());
        review.setUpdatedDate(LocalDate.now());

        reviewRepo.save(review);
        return toDTO(review);
    }

    public List<EmployeeReviewResponseDTO> getByEmployee(Long employeeId) {
        return reviewRepo.findByEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private EmployeeReviewResponseDTO toDTO(EmployeeReview r) {
        return EmployeeReviewResponseDTO.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .month(r.getMonth())
                .year(r.getYear())
                .kpi1(r.getKpi1()).kpi2(r.getKpi2()).kpi3(r.getKpi3()).kpi4(r.getKpi4()).kpi5(r.getKpi5())
                .review1(r.getReview1()).review2(r.getReview2()).review3(r.getReview3()).review4(r.getReview4())
                .review5(r.getReview5())
                .jobCode(r.getJobCode())
                .employeeComments(r.getEmployeeComments())
                .managerComments(r.getManagerComments())
                .build();
    }
}

