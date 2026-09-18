package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.leave.LeavePolicyRequestDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.service.LeavePolicyService;
import com.erp.service.hrsettings.JobCodeService;
import com.erp.service.salary.EmployeeCompensationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies Qatar labour-law defaults at company level (OT multipliers, wage floors,
 * and the statutory leave matrix for every company role). Used on new company create,
 * one-shot migration bootstrap, and the HR "Reset Qatar leave defaults" action.
 */
@Service
public class QatarLaborLawDefaultsService {

    public static final String ONE_TIME_TASK_KEY = "qatar_labor_law_defaults_v1";

    public static final BigDecimal OT_DAY_MULTIPLIER = new BigDecimal("1.25");
    public static final BigDecimal OT_NIGHT_FRIDAY_HOLIDAY_MULTIPLIER = new BigDecimal("1.50");
    public static final LocalTime OT_NIGHT_START = LocalTime.of(21, 0);
    public static final LocalTime OT_NIGHT_END = LocalTime.of(3, 0);
    public static final BigDecimal OT_MAX_HOURS_PER_DAY = new BigDecimal("2.00");
    public static final BigDecimal MINIMUM_MONTHLY_WAGE = new BigDecimal("1000.00");
    public static final BigDecimal DEFAULT_HOUSING_ALLOWANCE = new BigDecimal("500.00");
    public static final BigDecimal DEFAULT_FOOD_ALLOWANCE = new BigDecimal("300.00");

    private final CompanyRepository companyRepository;
    private final JobCodeRepository jobCodeRepository;
    private final CompanyLeavePolicyRepository leavePolicyRepository;
    private final LeavePolicyService leavePolicyService;
    private final EmployeeCompensationService employeeCompensationService;
    private final JobCodeService jobCodeService;

    public QatarLaborLawDefaultsService(
            CompanyRepository companyRepository,
            JobCodeRepository jobCodeRepository,
            CompanyLeavePolicyRepository leavePolicyRepository,
            LeavePolicyService leavePolicyService,
            @Lazy EmployeeCompensationService employeeCompensationService,
            @Lazy JobCodeService jobCodeService) {
        this.companyRepository = companyRepository;
        this.jobCodeRepository = jobCodeRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.leavePolicyService = leavePolicyService;
        this.employeeCompensationService = employeeCompensationService;
        this.jobCodeService = jobCodeService;
    }

    /** Company labor columns + leave matrix + balance sync. */
    @Transactional
    public void applyToCompany(Long companyId) {
        applyLaborColumns(companyId);
        applyLeaveDefaults(companyId);
    }

    /** Leave types only (Sick/Maternity/Hajj/Marriage/Bereavement) — does not touch Annual/Emergency/Unpaid. */
    @Transactional
    public void applyLeaveDefaults(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Leave policies are keyed by JOB CODE. Seed statutory defaults for every
        // active job code, plus any job code that already carries a policy (so a
        // "reset defaults" re-applies to already-configured codes, including legacy
        // rows). If the company has no job codes yet, there is nothing to seed —
        // defaults get applied once job codes exist and the admin resets again.
        Set<String> jobCodes = new LinkedHashSet<>();
        for (JobCode jc : jobCodeRepository.findByCompany_IdAndActiveTrue(companyId)) {
            if (jc.getCode() != null && !jc.getCode().isBlank()) {
                jobCodes.add(jc.getCode().trim());
            }
        }
        leavePolicyRepository.findByCompanyOrderByIdDesc(company).forEach(p -> {
            if (p.getJobCode() != null && !p.getJobCode().isBlank()) {
                jobCodes.add(p.getJobCode().trim());
            }
        });
        if (jobCodes.isEmpty()) {
            return;
        }

        List<LeavePolicyRequestDTO> dtos = new ArrayList<>();
        for (String jobCode : jobCodes) {
            dtos.add(leave(jobCode, "Sick Leave", 7, false, null, false, null));
            dtos.add(leave(jobCode, "Maternity Leave", 50, true, "FEMALE", false, null));
            dtos.add(leave(jobCode, "Hajj Leave", 10, false, null, true, "Islam"));
            dtos.add(leave(jobCode, "Marriage Leave", 3, false, null, false, null));
            dtos.add(leave(jobCode, "Bereavement Leave", 3, false, null, false, null));
        }

        leavePolicyService.savePoliciesAsSystem(companyId, dtos);
    }

    @Transactional
    public void applyLaborColumns(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        company.setOtDayRateMultiplier(OT_DAY_MULTIPLIER);
        company.setOtNightFridayHolidayRateMultiplier(OT_NIGHT_FRIDAY_HOLIDAY_MULTIPLIER);
        company.setOtNightStartTime(OT_NIGHT_START);
        company.setOtNightEndTime(OT_NIGHT_END);
        company.setOtMaxHoursPerDay(OT_MAX_HOURS_PER_DAY);
        company.setMinimumMonthlyWage(MINIMUM_MONTHLY_WAGE);
        company.setDefaultHousingAllowance(DEFAULT_HOUSING_ALLOWANCE);
        company.setDefaultFoodAllowance(DEFAULT_FOOD_ALLOWANCE);
        companyRepository.save(company);
        employeeCompensationService.floorStatutoryCompensationFromCompany(companyId);
        jobCodeService.floorMinSalariesFromCompany(companyId);
    }

    private static LeavePolicyRequestDTO leave(
            String jobCode,
            String leaveType,
            int days,
            boolean genderRestricted,
            String allowedGender,
            boolean religionRestricted,
            String allowedReligion) {
        LeavePolicyRequestDTO dto = new LeavePolicyRequestDTO();
        dto.setJobCode(jobCode);
        dto.setLeaveType(leaveType);
        dto.setDefaultDays(days);
        dto.setPaid(true);
        dto.setGenderRestricted(genderRestricted);
        dto.setAllowedGender(allowedGender);
        dto.setReligionRestricted(religionRestricted);
        dto.setAllowedReligion(allowedReligion);
        return dto;
    }
}
