package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.salary.CompensationRequestDTO;
import com.erp.dto.salary.SalaryResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EmployeeCompensationService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;

    public EmployeeCompensationService(
            EmployeeRepository employeeRepo,
            EmployeeCompensationRepository compensationRepo) {
        this.employeeRepo = employeeRepo;
        this.compensationRepo = compensationRepo;
    }

    /* ================= CREATE ================= */

    @Transactional
    public void createSalary(Long employeeId, CompensationRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCompensation c = new EmployeeCompensation();
        c.setEmployee(employee);

        mapAndCalculate(c, dto);

        compensationRepo.save(c);
    }

    /* ================= UPDATE ================= */

    @Transactional
    public void updateSalary(Long employeeId, CompensationRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCompensation c =
                compensationRepo.findByEmployeeAndStatus(employee, "ACTIVE")
                        .orElseThrow(() ->
                                new RuntimeException("Active salary not found"));

        mapAndCalculate(c, dto);

        compensationRepo.save(c);
    }

    /* ================= GET ACTIVE ================= */

    public SalaryResponseDTO getActiveCompensation(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCompensation c =
                compensationRepo.findActiveByEmployee(employee)
                        .orElse(null);

        if (c == null) return null;

        SalaryResponseDTO dto = new SalaryResponseDTO();
        dto.setBasicSalary(c.getBasicSalary());

        dto.setTransportation(c.getTransportation());
        dto.setTransportationAllowance(c.getTransportationAllowance());

        dto.setTravel(c.getTravel());
        dto.setTravelAllowance(c.getTravelAllowance());

        dto.setHousing(c.getHousing());
        dto.setHousingAllowance(c.getHousingAllowance());

        dto.setOtherAllowance(c.getOtherAllowance());
        dto.setTotalCompensation(c.getTotalCompensation());

        dto.setStatus(c.getStatus());
        dto.setEffectiveFrom(c.getEffectiveFrom());
        dto.setEffectiveTo(c.getEffectiveTo());

        return dto;
    }

    /* ================= COMMON LOGIC ================= */

    private void mapAndCalculate(EmployeeCompensation c,
                                 CompensationRequestDTO dto) {

        c.setBasicSalary(dto.getBasicSalary());

        c.setTransportation(dto.getTransportation());
        c.setTransportationAllowance(
                Boolean.TRUE.equals(dto.getTransportation())
                        ? dto.getTransportationAllowance()
                        : 0
        );

        c.setTravel(dto.getTravel());
        c.setTravelAllowance(
                Boolean.TRUE.equals(dto.getTravel())
                        ? dto.getTravelAllowance()
                        : 0
        );

        c.setHousing(dto.getHousing());
        c.setHousingAllowance(
                Boolean.TRUE.equals(dto.getHousing())
                        ? dto.getHousingAllowance()
                        : 0
        );

        c.setOtherAllowance(
                dto.getOtherAllowance() != null
                        ? dto.getOtherAllowance()
                        : 0
        );

        double total =
                c.getBasicSalary()
                        + c.getTransportationAllowance()
                        + c.getTravelAllowance()
                        + c.getHousingAllowance()
                        + c.getOtherAllowance();

        c.setTotalCompensation(total);

        c.setStatus(dto.getStatus()); // "ACTIVE" / "INACTIVE"
        c.setEffectiveFrom(dto.getEffectiveFrom());
        c.setEffectiveTo(dto.getEffectiveTo());
    }
}
