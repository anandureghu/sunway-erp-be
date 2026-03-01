package com.erp.dto.hr;


import com.erp.domain.enums.ContractStatus;
import com.erp.domain.enums.ContractType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ContractRequestDTO {

    @NotNull(message = "Contract type is required")
    private ContractType contractType;

    @NotNull(message = "Status is required")
    private ContractStatus status;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    private Integer noticePeriodDays;

    @Valid
    @NotNull(message = "At least one allowance is required")
    private List<AllowanceRequestDTO> allowances;
}