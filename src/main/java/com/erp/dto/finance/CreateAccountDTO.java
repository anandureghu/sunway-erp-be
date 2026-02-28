package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountDTO {

    private String accountCode;        // GL Code (e.g., "BANK-001")
    private String accountName;        // Name (Cash at Bank, Accounts Receivable, etc.)
    private String description;

    private String type;               // asset, liability, income, expense, equity
    private Long parentId;             // For hierarchy (optional)

    private BigDecimal openingBalance;    // ⭐ New – starting balance for the account

    private String accountNo;
    private String interCompanyNumber;           // asset, liability, income, expense, equity
    private Long departmentId;
    private String projectCode;
}
