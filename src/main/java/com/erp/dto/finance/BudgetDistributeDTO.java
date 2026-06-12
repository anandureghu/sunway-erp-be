package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDistributeDTO {
    private Long creditAccountId;
    private BigDecimal amount;
    private String notes;
    private LocalDate postedDate;
}
