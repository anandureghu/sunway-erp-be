package com.erp.dto.loan;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class LoanRequestDTO {

    private String loanCode;
    private Double loanAmount;
    private Integer loanPeriod; // months
    private Double monthlyDeduction;
    private LocalDate startDate;
}
