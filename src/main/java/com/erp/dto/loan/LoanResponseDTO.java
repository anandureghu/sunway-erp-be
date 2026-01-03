package com.erp.dto.loan;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class LoanResponseDTO {

    private Long id;
    private String loanCode;
    private Double loanAmount;
    private Integer loanPeriod;
    private Double monthlyDeduction;
    private Double balance;
    private String status;
    private LocalDate startDate;
}
