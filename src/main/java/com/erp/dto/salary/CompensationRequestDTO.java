package com.erp.dto.salary;

import com.erp.domain.enums.BenefitType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CompensationRequestDTO {

    /* ================= SALARY ================= */
    private Double basicSalary;

    /* ================= HOUSING ================= */
    private BenefitType housingType;        // NONE | ALLOWANCE | COMPANY_PROVIDED
    private Double housingAllowance;        // used only if housingType = ALLOWANCE

    /* ================= TRANSPORTATION ================= */
    private BenefitType transportationType;
    private Double transportationAllowance;

    /* ================= TRAVEL ================= */
    private BenefitType travelType;
    private Double travelAllowance;

    /* ================= OTHER ================= */
    private Double otherAllowance;

    /* ================= FOOD ================= */
    private Double foodAllowance;

    /**
     * When true (or amount null), housing tracks company default.
     * Once false, company default changes no longer overwrite this row.
     */
    private Boolean housingFollowsCompanyDefault;

    private Boolean foodFollowsCompanyDefault;

    /* ================= STATUS ================= */
    private String status; // ACTIVE / INACTIVE

    /* ================= EFFECTIVE ================= */
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
