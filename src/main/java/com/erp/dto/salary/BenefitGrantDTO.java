package com.erp.dto.salary;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class BenefitGrantDTO {
    private Long id;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String benefitType;
    private String benefitTypeLabel;
    private BigDecimal amount;
    /** yyyy-MM-01 — the month whose payroll pays the grant. */
    private LocalDate payMonth;
    private String description;
    private String documentUrl;
    private String status;
    private Long payrollId;
    private LocalDateTime createdAt;
}
