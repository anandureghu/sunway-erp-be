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
    private Double totalCompensation;

    /* ================= STATUS ================= */
    private String status; // ACTIVE / INACTIVE
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    /* ================= CURRENCY (NEW) ================= */
    private String currencyCode;
    private String currencySymbol;
}
