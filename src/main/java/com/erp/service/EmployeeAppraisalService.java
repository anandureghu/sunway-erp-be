package com.erp.service;
import com.erp.domain.Employee;
import com.erp.domain.EmployeeAppraisal;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.repo.EmployeeAppraisalRepository;
import com.erp.repo.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeAppraisalService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeAppraisalRepository appraisalRepo;

    // ---------- GET ----------
    public EmployeeAppraisalResponseDTO get(Long employeeId, String month, Integer year) {
        return appraisalRepo
                .findByEmployeeIdAndMonthAndYear(employeeId, month, year)
                .map(this::toDTO)
                .orElse(null); // IMPORTANT
    }

    // ---------- CREATE ----------
    public EmployeeAppraisalResponseDTO create(
            Long employeeId,
            String month,
            Integer year,
            EmployeeAppraisalRequestDTO dto
    ) {
        if (appraisalRepo.existsByEmployeeIdAndMonthAndYear(employeeId, month, year)) {
            throw new RuntimeException("Appraisal already exists for this period");
        }

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeAppraisal appraisal = EmployeeAppraisal.builder()
                .employee(employee)
                .month(month)
                .year(year)
                .jobCode(dto.getJobCode())
                .employeeComments(dto.getEmployeeComments())
                .managerComments(dto.getManagerComments())
                .build();

        appraisalRepo.save(appraisal);
        return toDTO(appraisal);
    }

    // ---------- UPDATE ----------
    public EmployeeAppraisalResponseDTO update(
            Long employeeId,
            String month,
            Integer year,
            EmployeeAppraisalRequestDTO dto
    ) {
        EmployeeAppraisal appraisal = appraisalRepo
                .findByEmployeeIdAndMonthAndYear(employeeId, month, year)
                .orElseThrow(() -> new RuntimeException("Appraisal not found"));

        appraisal.setJobCode(dto.getJobCode());
        appraisal.setEmployeeComments(dto.getEmployeeComments());
        appraisal.setManagerComments(dto.getManagerComments());

        return toDTO(appraisal);
    }

    private EmployeeAppraisalResponseDTO toDTO(EmployeeAppraisal a) {
        return EmployeeAppraisalResponseDTO.builder()
                .id(a.getId())
                .employeeId(a.getEmployee().getId())
                .month(a.getMonth())
                .year(a.getYear())
                .jobCode(a.getJobCode())
                .employeeComments(a.getEmployeeComments())
                .managerComments(a.getManagerComments())
                .build();
    }
}
