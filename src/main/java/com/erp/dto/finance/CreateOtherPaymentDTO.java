package com.erp.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOtherPaymentDTO {
    private Long companyId;
    /** RENT, EMPLOYEE_REIMBURSEMENT, VENDOR_REIMBURSEMENT, UTILITIES, OTHER */
    private String expenseCategory;
    /** Free text: who the expense is paid to. */
    private String payee;
    private BigDecimal amount;
    /** Defaults to today when omitted. */
    private LocalDate effectiveDate;
    private String notes;
}
