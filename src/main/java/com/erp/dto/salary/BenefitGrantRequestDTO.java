package com.erp.dto.salary;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BenefitGrantRequestDTO {
    private Long employeeId;
    /** ANNUAL_TICKET, BONUS or REIMBURSEMENT. */
    private String benefitType;
    private BigDecimal amount;
    /** Pay month as yyyy-MM (a full date is accepted; only year and month are used). */
    private String payMonth;
    private String description;
}
