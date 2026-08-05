package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.enums.BenefitType;
import com.erp.domain.hrsettings.JobCode;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.security.AppModule;
import com.erp.dto.salary.CompensationRequestDTO;
import com.erp.dto.salary.SalaryResponseDTO;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.security.guard.EmployeeAccessGuard;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class EmployeeCompensationService {
    private static final String CURRENCY_WARNING =
            "Set currency from company profile to enable currency display.";

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final EmployeeAccessGuard accessGuard;

    public EmployeeCompensationService(
            EmployeeRepository employeeRepo,
            EmployeeCompensationRepository compensationRepo,
            EmployeeCurrentJobRepo currentJobRepo,
            EmployeeAccessGuard accessGuard) {
        this.employeeRepo = employeeRepo;
        this.compensationRepo = compensationRepo;
        this.currentJobRepo = currentJobRepo;
        this.accessGuard = accessGuard;
    }

    /* ================= CREATE ================= */

    @Transactional
    public void createSalary(Long employeeId, CompensationRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        accessGuard.assertCanWrite(employee, AppModule.SALARY);

        applyBasicSalaryDefault(dto, employee);
        validateMinimumBasicSalary(dto.getBasicSalary(), employee);
        validateSalaryBand(employeeId, dto.getBasicSalary());

        // 🔒 Deactivate old ACTIVE salary
        compensationRepo.findByEmployeeAndStatus(employee, "ACTIVE")
                .ifPresent(old -> {
                    old.setStatus("INACTIVE");
                    compensationRepo.save(old);
                });

        EmployeeCompensation c = new EmployeeCompensation();
        c.setEmployee(employee);

        mapAndCalculate(c, dto, employee);

        c.setStatus("ACTIVE");

        compensationRepo.save(c);
    }

    /* ================= UPDATE ================= */

    @Transactional
    public void updateSalary(Long employeeId, CompensationRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        accessGuard.assertCanWrite(employee, AppModule.SALARY);

        EmployeeCompensation c = compensationRepo
                .findByEmployeeAndStatus(employee, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Active salary not found"));

        applyBasicSalaryDefault(dto, employee);
        validateMinimumBasicSalary(dto.getBasicSalary(), employee);
        validateSalaryBand(employeeId, dto.getBasicSalary());

        mapAndCalculate(c, dto, employee);

        compensationRepo.save(c);
    }

    /* ================= GET ACTIVE ================= */

    public SalaryResponseDTO getActiveCompensation(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        accessGuard.assertCanRead(employee, AppModule.SALARY);

        EmployeeCompensation c =
                compensationRepo.findActiveByEmployee(employee).orElse(null);

        if (c == null) return null;

        SalaryResponseDTO dto = new SalaryResponseDTO();

        // 🔥 CURRENCY FROM COMPANY - WITH NULL CHECKS
        if (employee.getCompany() != null && employee.getCompany().getCurrency() != null) {
            dto.setCurrencyCode(
                    employee.getCompany().getCurrency().getCurrencyCode());

            dto.setCurrencySymbol(
                    employee.getCompany().getCurrency().getCurrencySymbol());
            dto.setCurrencyConfigured(true);
            dto.setCurrencyWarning(null);
        } else {
            dto.setCurrencyCode(null);
            dto.setCurrencySymbol(null);
            dto.setCurrencyConfigured(false);
            dto.setCurrencyWarning(CURRENCY_WARNING);
        }

        dto.setBasicSalary(c.getBasicSalary());

        dto.setHousingType(c.getHousingType());
        boolean housingFollows = c.isHousingFollowsCompanyDefault();
        Double housingAmt = c.getHousingAllowance();
        if (housingFollows && c.getHousingType() == BenefitType.ALLOWANCE) {
            Double companyHousing = companyDefaultHousing(employee);
            if (companyHousing != null) {
                // Never display below company floor; never downgrade stored higher values.
                housingAmt = Math.max(safe(housingAmt), companyHousing);
            }
        }
        dto.setHousingAllowance(housingAmt);
        dto.setHousingFollowsCompanyDefault(housingFollows);

        dto.setTransportationType(c.getTransportationType());
        dto.setTransportationAllowance(c.getTransportationAllowance());

        dto.setTravelType(c.getTravelType());
        dto.setTravelAllowance(c.getTravelAllowance());

        dto.setOtherAllowance(c.getOtherAllowance());
        boolean foodFollows = c.isFoodFollowsCompanyDefault();
        Double foodAmt = c.getFoodAllowance() != null ? c.getFoodAllowance() : 0.0;
        if (foodFollows) {
            Double companyFood = companyDefaultFood(employee);
            if (companyFood != null) {
                foodAmt = Math.max(safe(foodAmt), companyFood);
            }
        }
        dto.setFoodAllowance(foodAmt);
        dto.setFoodFollowsCompanyDefault(foodFollows);

        double total =
                safe(c.getBasicSalary())
                        + safe(housingAmt)
                        + safe(c.getTransportationAllowance())
                        + safe(c.getTravelAllowance())
                        + safe(c.getOtherAllowance())
                        + safe(foodAmt);
        dto.setTotalCompensation(total);

        dto.setStatus(c.getStatus());
        dto.setEffectiveFrom(c.getEffectiveFrom());
        dto.setEffectiveTo(c.getEffectiveTo());

        return dto;
    }

    /**
     * Floor ACTIVE compensation amounts up to company statutory defaults.
     * Never lowers existing higher values (including follower rows when defaults drop).
     */
    @Transactional
    public int floorStatutoryCompensationFromCompany(Long companyId) {
        if (companyId == null) {
            return 0;
        }
        var rows = compensationRepo.findActiveByCompanyId(companyId);
        int updated = 0;
        for (EmployeeCompensation c : rows) {
            Employee employee = c.getEmployee();
            if (employee == null || employee.getCompany() == null) {
                continue;
            }
            boolean dirty = false;

            Double minBasic = companyMinBasic(employee);
            if (minBasic != null && safe(c.getBasicSalary()) < minBasic) {
                c.setBasicSalary(minBasic);
                dirty = true;
            }

            Double housingFloor = companyDefaultHousing(employee);
            if (housingFloor != null
                    && c.getHousingType() == BenefitType.ALLOWANCE
                    && safe(c.getHousingAllowance()) < housingFloor) {
                c.setHousingAllowance(housingFloor);
                dirty = true;
            }

            Double foodFloor = companyDefaultFood(employee);
            if (foodFloor != null && safe(c.getFoodAllowance()) < foodFloor) {
                c.setFoodAllowance(foodFloor);
                dirty = true;
            }

            if (dirty) {
                recalculateTotal(c);
                compensationRepo.save(c);
                updated++;
            }
        }
        return updated;
    }

    /* ================= CORE LOGIC ================= */

    private void mapAndCalculate(EmployeeCompensation c, CompensationRequestDTO dto, Employee employee) {

        if (dto.getBasicSalary() == null) {
            throw new RuntimeException("Basic salary is required");
        }

        c.setBasicSalary(dto.getBasicSalary());

        Double companyHousingDefault = companyDefaultHousing(employee);
        Double companyFoodDefault = companyDefaultFood(employee);

        // HOUSING — follows company default until edited once
        BenefitType housingType = defaultType(dto.getHousingType());
        c.setHousingType(housingType);
        if (housingType == BenefitType.ALLOWANCE) {
            boolean housingFollows = resolveFollows(
                    dto.getHousingFollowsCompanyDefault(),
                    dto.getHousingAllowance(),
                    c.isHousingFollowsCompanyDefault(),
                    companyHousingDefault);
            c.setHousingFollowsCompanyDefault(housingFollows);
            if (housingFollows) {
                Double housing = companyHousingDefault != null ? companyHousingDefault : dto.getHousingAllowance();
                if (housing == null) {
                    throw new RuntimeException("Housing allowance required");
                }
                // Never downgrade an existing higher allowance when company default drops.
                if (c.getHousingAllowance() != null) {
                    housing = Math.max(housing, c.getHousingAllowance());
                }
                c.setHousingAllowance(housing);
            } else {
                Double housing = dto.getHousingAllowance();
                if (housing == null) {
                    throw new RuntimeException("Housing allowance required");
                }
                // Floor custom amounts up to company default; never store below floor.
                if (companyHousingDefault != null && housing < companyHousingDefault) {
                    housing = companyHousingDefault;
                }
                c.setHousingAllowance(housing);
            }
        } else {
            c.setHousingAllowance(0.0);
            // Switching away from allowance keeps "follows" so flipping back re-attaches.
            c.setHousingFollowsCompanyDefault(true);
        }

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

        // FOOD — follows company default until edited once
        boolean foodFollows = resolveFollows(
                dto.getFoodFollowsCompanyDefault(),
                dto.getFoodAllowance(),
                c.isFoodFollowsCompanyDefault(),
                companyFoodDefault);
        c.setFoodFollowsCompanyDefault(foodFollows);
        if (foodFollows) {
            Double food = companyFoodDefault != null
                    ? companyFoodDefault
                    : (dto.getFoodAllowance() != null ? dto.getFoodAllowance() : 0.0);
            if (c.getFoodAllowance() != null) {
                food = Math.max(food, c.getFoodAllowance());
            }
            c.setFoodAllowance(food);
        } else {
            Double food = safe(dto.getFoodAllowance());
            if (companyFoodDefault != null && food < companyFoodDefault) {
                food = companyFoodDefault;
            }
            c.setFoodAllowance(food);
        }

        recalculateTotal(c);

        c.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        c.setEffectiveFrom(dto.getEffectiveFrom());
        c.setEffectiveTo(dto.getEffectiveTo());
    }

    /**
     * Null/zero basic salary is treated as unconfigured → company min basic salary.
     */
    private void applyBasicSalaryDefault(CompensationRequestDTO dto, Employee employee) {
        Double basic = dto.getBasicSalary();
        if (basic != null && basic > 0) {
            return;
        }
        Double minBasic = companyMinBasic(employee);
        if (minBasic != null) {
            dto.setBasicSalary(minBasic);
        }
    }

    private void validateMinimumBasicSalary(Double basicSalary, Employee employee) {
        Double minBasic = companyMinBasic(employee);
        if (minBasic == null || basicSalary == null) {
            return;
        }
        if (basicSalary < minBasic) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Basic salary cannot be below the company min basic salary of "
                            + BigDecimal.valueOf(minBasic).toPlainString());
        }
    }

    /**
     * Explicit flag wins. Null amount = follow company default. Explicit amount
     * matching the company default keeps following only if not already customized;
     * any other amount marks the field as edited once.
     */
    private boolean resolveFollows(
            Boolean requestedFollows,
            Double amount,
            boolean previousFollows,
            Double companyDefault) {
        if (requestedFollows != null) {
            return requestedFollows;
        }
        if (amount == null) {
            return true;
        }
        if (companyDefault != null && almostEqual(amount, companyDefault)) {
            return previousFollows;
        }
        return false;
    }

    private void recalculateTotal(EmployeeCompensation c) {
        c.setTotalCompensation(
                safe(c.getBasicSalary())
                        + safe(c.getHousingAllowance())
                        + safe(c.getTransportationAllowance())
                        + safe(c.getTravelAllowance())
                        + safe(c.getOtherAllowance())
                        + safe(c.getFoodAllowance()));
    }

    private static boolean almostEqual(Double a, Double b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(a - b) < 0.005;
    }

    private Double companyMinBasic(Employee employee) {
        if (employee.getCompany() == null || employee.getCompany().getMinimumMonthlyWage() == null) {
            return null;
        }
        return employee.getCompany().getMinimumMonthlyWage().doubleValue();
    }

    private Double companyDefaultHousing(Employee employee) {
        if (employee.getCompany() == null || employee.getCompany().getDefaultHousingAllowance() == null) {
            return null;
        }
        return employee.getCompany().getDefaultHousingAllowance().doubleValue();
    }

    private Double companyDefaultFood(Employee employee) {
        if (employee.getCompany() == null || employee.getCompany().getDefaultFoodAllowance() == null) {
            return null;
        }
        return employee.getCompany().getDefaultFoodAllowance().doubleValue();
    }

    /* ================= SALARY BAND ENFORCEMENT ================= */

    /**
     * Reject a basic salary that falls outside the min/max band of the employee's
     * job code (salary grade). No-op when the employee has no current job / job
     * code, or the grade has no configured band.
     */
    private void validateSalaryBand(Long employeeId, Double basicSalary) {
        if (basicSalary == null) {
            return;
        }
        currentJobRepo.findByEmployee_Id(employeeId).ifPresent(currentJob -> {
            JobCode jobCode = currentJob.getJobCode();
            if (jobCode == null) {
                return;
            }

            BigDecimal basic = BigDecimal.valueOf(basicSalary);
            BigDecimal min = jobCode.getMinSalary();
            BigDecimal max = jobCode.getMaxSalary();
            String grade = jobCode.getSalaryGrade();
            String forGrade = (grade != null && !grade.isBlank())
                    ? " for salary grade " + grade
                    : "";

            if (max != null && max.signum() > 0 && basic.compareTo(max) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Basic salary exceeds the maximum of " + max.toPlainString() + forGrade);
            }
            if (min != null && min.signum() > 0 && basic.compareTo(min) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Basic salary is below the minimum of " + min.toPlainString() + forGrade);
            }
        });
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
