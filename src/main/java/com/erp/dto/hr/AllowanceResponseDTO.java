package com.erp.dto.hr;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AllowanceResponseDTO {

    private Long id;
    private String allowanceType;
    private BigDecimal amount;
    private LocalDate effectiveDate;
    private String note;
}