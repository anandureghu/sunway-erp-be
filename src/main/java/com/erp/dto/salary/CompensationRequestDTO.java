package com.erp.dto.salary;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CompensationRequestDTO {

    private Double basicSalary;
    private Boolean transportation;
    private Double transportationAllowance;
    private Boolean travel;
    private Double travelAllowance;
    private Boolean housing;
    private Double housingAllowance;
    private Double otherAllowance;
    private String status; // ACTIVE / INACTIVE
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
