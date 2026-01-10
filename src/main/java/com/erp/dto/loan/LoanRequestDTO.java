package com.erp.dto.loan;

import com.erp.domain.LoanType;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LoanRequestDTO {

    private LoanType loanType;
    private Double loanAmount;
    private Integer loanPeriod; // months
    private LocalDate startDate;
    private String notes;
}