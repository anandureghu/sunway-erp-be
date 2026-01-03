package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeAppraisal;
import com.erp.dto.appraisal.EmployeePerformanceRequestDTO;
import com.erp.dto.appraisal.EmployeePerformanceResponseDTO;
import com.erp.repo.EmployeeAppraisalRepository;
import com.erp.repo.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeePerformanceService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeAppraisalRepository appraisalRepo;

    // normalize month (CRITICAL)
    private String normalizeMonth(String month) {
        return month.toLowerCase().trim();
    }

    // ---------------- GET ----------------
    public EmployeePerformanceResponseDTO get(
            Long employeeId,
            String month,
            Integer year
    ) {
        return appraisalRepo
                .findByEmployeeIdAndMonthAndYear(
                        employeeId,
                        normalizeMonth(month),
                        year
                )
                .map(this::toDTO)
                .orElse(null);
    }

    // ---------------- CREATE ----------------
    public EmployeePerformanceResponseDTO create(
            Long employeeId,
            String month,
            Integer year,
            EmployeePerformanceRequestDTO dto
    ) {
        String m = normalizeMonth(month);

        EmployeeAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndMonthAndYear(employeeId, m, year)
                .orElseGet(() -> {
                    Employee employee = employeeRepo.findById(employeeId)
                            .orElseThrow(() -> new RuntimeException("Employee not found"));

                    return EmployeeAppraisal.builder()
                            .employee(employee)
                            .month(m)
                            .year(year)
                            .build();
                });

        mergePerformance(appraisal, dto);
        appraisalRepo.save(appraisal);

        return toDTO(appraisal);
    }

    // ---------------- UPDATE ----------------
    public EmployeePerformanceResponseDTO update(
            Long employeeId,
            String month,
            Integer year,
            EmployeePerformanceRequestDTO dto
    ) {
        EmployeeAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndMonthAndYear(
                        employeeId,
                        normalizeMonth(month),
                        year
                )
                .orElseThrow(() -> new RuntimeException("Performance not found"));

        mergePerformance(appraisal, dto);
        return toDTO(appraisal);
    }

    // ---------------- MERGE ----------------
    private void mergePerformance(
            EmployeeAppraisal a,
            EmployeePerformanceRequestDTO d
    ) {
        a.setKpi1(d.getKpi1());
        a.setReview1(d.getReview1());
        a.setKpi2(d.getKpi2());
        a.setReview2(d.getReview2());
        a.setKpi3(d.getKpi3());
        a.setReview3(d.getReview3());
        a.setKpi4(d.getKpi4());
        a.setReview4(d.getReview4());
        a.setKpi5(d.getKpi5());
        a.setReview5(d.getReview5());
    }

    // ---------------- DTO ----------------
    private EmployeePerformanceResponseDTO toDTO(EmployeeAppraisal a) {
        return EmployeePerformanceResponseDTO.builder()
                .employeeId(a.getEmployee().getId())
                .month(a.getMonth())
                .year(a.getYear())
                .kpi1(a.getKpi1())
                .review1(a.getReview1())
                .kpi2(a.getKpi2())
                .review2(a.getReview2())
                .kpi3(a.getKpi3())
                .review3(a.getReview3())
                .kpi4(a.getKpi4())
                .review4(a.getReview4())
                .kpi5(a.getKpi5())
                .review5(a.getReview5())
                .build();
    }
}
