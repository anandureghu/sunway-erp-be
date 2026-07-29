package com.erp.dto.salary;

import com.erp.domain.enums.BenefitType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SalaryResponseDTO {

    /* ================= BASIC ================= */
    private Double basicSalary;

    /* ================= HOUSING ================= */
    private BenefitType housingType;
    private Double housingAllowance;

    /* ================= TRANSPORTATION ================= */
    private BenefitType transportationType;
    private Double transportationAllowance;

    /* ================= TRAVEL ================= */
    private BenefitType travelType;
    private Double travelAllowance;

    /* ================= OTHER ================= */
    private Double otherAllowance;
    private Double foodAllowance;
    private Double totalCompensation;

    /** Still linked to company statutory defaults (auto-updates until edited once). */
    private Boolean housingFollowsCompanyDefault;
    private Boolean foodFollowsCompanyDefault;

    /* ================= STATUS ================= */
    private String status; // ACTIVE / INACTIVE
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    /* ================= CURRENCY (NEW) ================= */
    private String currencyCode;
    private String currencySymbol;
    private Boolean currencyConfigured;
    private String currencyWarning;
}
