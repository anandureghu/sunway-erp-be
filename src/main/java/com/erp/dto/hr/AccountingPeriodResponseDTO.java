package com.erp.dto.hr;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriodResponseDTO {

    private Long id;
    private String periodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}