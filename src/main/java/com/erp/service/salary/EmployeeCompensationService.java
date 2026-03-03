package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.enums.BenefitType;
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

        //  Deactivate old ACTIVE salary
        compensationRepo.findByEmployeeAndStatus(employee, "ACTIVE")
                .ifPresent(old -> {
                    old.setStatus("INACTIVE");
                    compensationRepo.save(old);
                });

        EmployeeCompensation c = new EmployeeCompensation();
        c.setEmployee(employee);

        mapAndCalculate(c, dto);

        c.setStatus("ACTIVE");

        compensationRepo.save(c);
    }

    /* ================= UPDATE ================= */

    @Transactional
    public void updateSalary(Long employeeId, CompensationRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCompensation c = compensationRepo
                .findByEmployeeAndStatus(employee, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Active salary not found"));

        mapAndCalculate(c, dto);

        compensationRepo.save(c);
    }

    /* ================= GET ACTIVE ================= */

    public SalaryResponseDTO getActiveCompensation(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCompensation c =
                compensationRepo.findActiveByEmployee(employee).orElse(null);

        if (c == null) return null;

        SalaryResponseDTO dto = new SalaryResponseDTO();

        //  CURRENCY FROM COMPANY
        dto.setCurrencyCode(
                employee.getCompany().getCurrency().getCurrencyCode());

        dto.setCurrencySymbol(
                employee.getCompany().getCurrency().getCurrencySymbol());

        dto.setBasicSalary(c.getBasicSalary());

        dto.setHousingType(c.getHousingType());
        dto.setHousingAllowance(c.getHousingAllowance());

        dto.setTransportationType(c.getTransportationType());
        dto.setTransportationAllowance(c.getTransportationAllowance());

        dto.setTravelType(c.getTravelType());
        dto.setTravelAllowance(c.getTravelAllowance());

        dto.setOtherAllowance(c.getOtherAllowance());
        dto.setTotalCompensation(c.getTotalCompensation());

        dto.setStatus(c.getStatus());
        dto.setEffectiveFrom(c.getEffectiveFrom());
        dto.setEffectiveTo(c.getEffectiveTo());

        return dto;
    }

    /* ================= CORE LOGIC ================= */

    private void mapAndCalculate(EmployeeCompensation c, CompensationRequestDTO dto) {

        if (dto.getBasicSalary() == null) {
            throw new RuntimeException("Basic salary is required");
        }

        c.setBasicSalary(dto.getBasicSalary());

        // HOUSING
        BenefitType housingType = defaultType(dto.getHousingType());
        c.setHousingType(housingType);
        c.setHousingAllowance(
                housingType == BenefitType.ALLOWANCE
                        ? require(dto.getHousingAllowance(), "Housing allowance required")
                        : 0.0
        );

        // TRANSPORT
        BenefitType transportType = defaultType(dto.getTransportationType());
        c.setTransportationType(transportType);
        c.setTransportationAllowance(
                transportType == BenefitType.ALLOWANCE
                        ? require(dto.getTransportationAllowance(), "Transportation allowance required")
                        : 0.0
        );

        // TRAVEL
        BenefitType travelType = defaultType(dto.getTravelType());
        c.setTravelType(travelType);
        c.setTravelAllowance(
                travelType == BenefitType.ALLOWANCE
                        ? require(dto.getTravelAllowance(), "Travel allowance required")
                        : 0.0
        );

        // OTHER
        c.setOtherAllowance(safe(dto.getOtherAllowance()));

        // TOTAL
        double total =
                c.getBasicSalary()
                        + c.getHousingAllowance()
                        + c.getTransportationAllowance()
                        + c.getTravelAllowance()
                        + c.getOtherAllowance();

        c.setTotalCompensation(total);

        c.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        c.setEffectiveFrom(dto.getEffectiveFrom());
        c.setEffectiveTo(dto.getEffectiveTo());
    }

    /* ================= HELPERS ================= */

    private BenefitType defaultType(BenefitType type) {
        return type != null ? type : BenefitType.COMPANY_PROVIDED;
    }

    private double safe(Double v) {
        return v != null ? v : 0.0;
    }

    private double require(Double v, String msg) {
        if (v == null) throw new RuntimeException(msg);
        return v;
    }
}
