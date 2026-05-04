package com.erp.dto.salary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollBatchResponseDTO {
    /** Number of active employees payroll rows created */
    private int generatedCount;
    /** Calendar month of pay date (yyyy-MM) */
    private String payrollMonth;
}
