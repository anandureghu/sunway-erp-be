package com.erp.dto.loan;

import com.erp.domain.LoanType;
import jakarta.persistence.Column;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LoanResponseDTO {
    private Long id;
    private String loanCode;
    private LoanType loanType;
    private Double loanAmount;
    private Integer loanPeriod;
    private Double monthlyDeduction;
    private Double balance;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long employeeId;
    private String employeeName;
    private String notes;
    private String currencyCode;
    private String currencySymbol;

}