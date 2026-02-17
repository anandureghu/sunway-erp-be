package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeAppraisal;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.repo.EmployeeAppraisalRepository;
import com.erp.repo.EmployeeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeAppraisalService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final EntityManager entityManager;

    /* ================= LIST ================= */

    @Transactional(readOnly = true)
    public List<EmployeeAppraisalResponseDTO> list(Long employeeId) {

        return appraisalRepository
                .findByEmployeeIdOrderByYearDescMonthDesc(employeeId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /* ================= CREATE ================= */

    public void create(Long employeeId, EmployeeAppraisalRequestDTO dto) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        validatePeriod(dto.getMonth(), dto.getYear());

        double overall = calculateAverage(
                dto.getRating1(),
                dto.getRating2(),
                dto.getRating3(),
                dto.getRating4(),
                dto.getRating5()
        );

        EmployeeAppraisal appraisal = EmployeeAppraisal.builder()
                .employee(employee)
                .month(normalizeMonth(dto.getMonth()))
                .year(dto.getYear())
                .jobCode(dto.getJobCode())

                .kpi1(dto.getKpi1())
                .review1(dto.getReview1())
                .rating1(dto.getRating1())

                .kpi2(dto.getKpi2())
                .review2(dto.getReview2())
                .rating2(dto.getRating2())

                .kpi3(dto.getKpi3())
                .review3(dto.getReview3())
                .rating3(dto.getRating3())

                .kpi4(dto.getKpi4())
                .review4(dto.getReview4())
                .rating4(dto.getRating4())

                .kpi5(dto.getKpi5())
                .review5(dto.getReview5())
                .rating5(dto.getRating5())

                .overallPerformance(overall)

                .employeeComments(dto.getEmployeeComments())
                .managerComments(dto.getManagerComments())
                .annualIncrement(dto.getAnnualIncrement())
                .build();

        try {
            appraisalRepository.save(appraisal);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Appraisal already exists for this employee and period"
            );
        }
    }

    /* ================= UPDATE ================= */

    public void update(Long employeeId, Long appraisalId, EmployeeAppraisalRequestDTO dto) {

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

        double overall = calculateAverage(
                dto.getRating1(),
                dto.getRating2(),
                dto.getRating3(),
                dto.getRating4(),
                dto.getRating5()
        );

        appraisal.setJobCode(dto.getJobCode());

        appraisal.setKpi1(dto.getKpi1());
        appraisal.setReview1(dto.getReview1());
        appraisal.setRating1(dto.getRating1());

        appraisal.setKpi2(dto.getKpi2());
        appraisal.setReview2(dto.getReview2());
        appraisal.setRating2(dto.getRating2());

        appraisal.setKpi3(dto.getKpi3());
        appraisal.setReview3(dto.getReview3());
        appraisal.setRating3(dto.getRating3());

        appraisal.setKpi4(dto.getKpi4());
        appraisal.setReview4(dto.getReview4());
        appraisal.setRating4(dto.getRating4());

        appraisal.setKpi5(dto.getKpi5());
        appraisal.setReview5(dto.getReview5());
        appraisal.setRating5(dto.getRating5());

        appraisal.setOverallPerformance(overall);

        appraisal.setEmployeeComments(dto.getEmployeeComments());
        appraisal.setManagerComments(dto.getManagerComments());
        appraisal.setAnnualIncrement(dto.getAnnualIncrement());

        appraisalRepository.saveAndFlush(appraisal);
        entityManager.refresh(appraisal);
    }

    /* ================= DELETE ================= */

    public void delete(Long employeeId, Long appraisalId) {

        boolean exists = appraisalRepository
                .existsByIdAndEmployeeId(appraisalId, employeeId);

        if (!exists) {
            throw new IllegalArgumentException("Appraisal not found");
        }

        appraisalRepository.deleteByIdAndEmployeeId(appraisalId, employeeId);
    }

    /* ================= CALCULATE AVERAGE ================= */

    private double calculateAverage(Integer... ratings) {

        int sum = 0;
        int count = 0;

        for (Integer r : ratings) {
            if (r != null) {
                sum += r;
                count++;
            }
        }

        return count == 0 ? 0 : (double) sum / count;
    }

    /* ================= VALIDATION ================= */

    private void validatePeriod(String month, Integer year) {

        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Month is required");
        }

        if (year == null || year < 1900 || year > 2100) {
            throw new IllegalArgumentException("Invalid year");
        }
    }

    private String normalizeMonth(String month) {
        return month.trim().toLowerCase();
    }

    /* ================= MAPPER ================= */

    private EmployeeAppraisalResponseDTO mapToResponse(EmployeeAppraisal a) {

        return EmployeeAppraisalResponseDTO.builder()
                .id(a.getId())
                .employeeId(a.getEmployee().getId())
                .month(a.getMonth())
                .year(a.getYear())
                .jobCode(a.getJobCode())

                .kpi1(a.getKpi1())
                .review1(a.getReview1())
                .rating1(a.getRating1())

                .kpi2(a.getKpi2())
                .review2(a.getReview2())
                .rating2(a.getRating2())

                .kpi3(a.getKpi3())
                .review3(a.getReview3())
                .rating3(a.getRating3())

                .kpi4(a.getKpi4())
                .review4(a.getReview4())
                .rating4(a.getRating4())

                .kpi5(a.getKpi5())
                .review5(a.getReview5())
                .rating5(a.getRating5())

                .overallPerformance(a.getOverallPerformance())

                .employeeComments(a.getEmployeeComments())
                .managerComments(a.getManagerComments())
                .annualIncrement(a.getAnnualIncrement())

                .createdDate(a.getCreatedDate())
                .updatedDate(a.getUpdatedDate())
                .build();
    }
}
