package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.dto.salary.BenefitsAdjustmentRequestDTO;
import com.erp.dto.salary.BenefitsAdjustmentResultDTO;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk benefits adjustment for HR Settings: raise selected pay components by a
 * percentage across a group of employees selected by grade code, department, or a
 * single employee. Only ACTIVE compensation records are touched, and the total is
 * recomputed after each change.
 */
@Service
@RequiredArgsConstructor
public class BenefitsAdjustmentService {

    private static final Set<String> ALL_ALLOWANCES =
            Set.of("HOUSING", "TRANSPORT", "FOOD", "TRAVEL", "OTHER");

    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final AuthContext authContext;

    @Transactional
    public BenefitsAdjustmentResultDTO adjust(BenefitsAdjustmentRequestDTO req) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        if (req.getScope() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a scope.");
        }
        if (req.getPercentage() == null || req.getPercentage().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a percentage greater than zero.");
        }

        // components to raise — default to every allowance when none chosen.
        Set<String> components = (req.getComponents() == null || req.getComponents().isEmpty())
                ? ALL_ALLOWANCES
                : req.getComponents().stream().map(String::toUpperCase).collect(Collectors.toSet());

        List<Employee> targets = resolveTargets(req, companyId);
        BigDecimal factor = BigDecimal.ONE.add(
                req.getPercentage().movePointLeft(2)); // 1 + pct/100

        List<String> adjusted = new ArrayList<>();
        for (Employee e : targets) {
            EmployeeCompensation comp =
                    compensationRepo.findByEmployeeAndStatus(e, "ACTIVE").orElse(null);
            if (comp == null) {
                continue;
            }
            applyIncrease(comp, components, factor);
            compensationRepo.save(comp);
            adjusted.add(fullName(e));
        }
        return new BenefitsAdjustmentResultDTO(targets.size(), adjusted.size(), adjusted);
    }

    private List<Employee> resolveTargets(BenefitsAdjustmentRequestDTO req, Long companyId) {
        switch (req.getScope()) {
            case EMPLOYEE -> {
                if (req.getEmployeeId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an employee.");
                }
                Employee e = employeeRepo.findById(req.getEmployeeId())
                        .filter(x -> belongsToCompany(x, companyId))
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Employee not found"));
                return List.of(e);
            }
            case DEPARTMENT -> {
                if (req.getDepartmentId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a department.");
                }
                return employeeRepo.findByDepartment_IdOrderByCreatedAtDesc(req.getDepartmentId())
                        .stream()
                        .filter(e -> belongsToCompany(e, companyId))
                        .filter(this::isActive)
                        .toList();
            }
            case GRADE_CODE -> {
                if (req.getGradeCode() == null || req.getGradeCode().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a grade code.");
                }
                String grade = req.getGradeCode().trim();
                return employeeRepo.findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId)
                        .stream()
                        .filter(this::isActive)
                        .filter(e -> grade.equalsIgnoreCase(gradeOf(e)))
                        .toList();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported scope.");
        }
    }

    /** The employee's current salary grade (JobCode.salaryGrade), or null. */
    private String gradeOf(Employee e) {
        try {
            var job = currentJobRepo.findByEmployee_Id(e.getId()).orElse(null);
            if (job != null && job.getJobCode() != null) {
                return job.getJobCode().getSalaryGrade();
            }
        } catch (Exception ignored) {
            // no current job — no grade
        }
        return null;
    }

    private void applyIncrease(EmployeeCompensation c, Set<String> components, BigDecimal factor) {
        if (components.contains("BASIC")) {
            c.setBasicSalary(scale(c.getBasicSalary(), factor));
        }
        if (components.contains("HOUSING")) {
            c.setHousingAllowance(scale(c.getHousingAllowance(), factor));
        }
        if (components.contains("TRANSPORT")) {
            c.setTransportationAllowance(scale(c.getTransportationAllowance(), factor));
        }
        if (components.contains("FOOD")) {
            c.setFoodAllowance(scale(c.getFoodAllowance(), factor));
        }
        if (components.contains("TRAVEL")) {
            c.setTravelAllowance(scale(c.getTravelAllowance(), factor));
        }
        if (components.contains("OTHER")) {
            c.setOtherAllowance(scale(c.getOtherAllowance(), factor));
        }
        c.setTotalCompensation(
                safe(c.getBasicSalary())
                        + safe(c.getHousingAllowance())
                        + safe(c.getTransportationAllowance())
                        + safe(c.getTravelAllowance())
                        + safe(c.getOtherAllowance())
                        + safe(c.getFoodAllowance()));
    }

    private Double scale(Double value, BigDecimal factor) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).multiply(factor)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private boolean isActive(Employee e) {
        return e.getStatus() == null || !e.getStatus().isDepartedOrInactive();
    }

    private boolean belongsToCompany(Employee e, Long companyId) {
        return e.getCompany() != null && companyId.equals(e.getCompany().getId());
    }

    private double safe(Double d) {
        return d == null ? 0.0 : d;
    }

    private String fullName(Employee e) {
        String f = e.getFirstName() == null ? "" : e.getFirstName();
        String l = e.getLastName() == null ? "" : e.getLastName();
        return (f + " " + l).trim();
    }
}
