package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountDTO {

    private Long companyId;            // ⭐ MUST HAVE — COA is company-scoped

    private String accountCode;        // GL Code (e.g., "BANK-001")
    private String accountName;        // Name (Cash at Bank, Accounts Receivable, etc.)
    private String description;

    private String type;               // asset, liability, income, expense, equity
    private Long parentId;             // For hierarchy (optional)

    private String currency;           // Default currency
    private String status;             // active / inactive

    private String glAccountClassTypeKey; // e.g., FIN_ASSET, FIN_LIABILITY
    private String glAccountType;         // e.g., BANK, AR, AP, REV, EXP

    private BigDecimal openingBalance;    // ⭐ New – starting balance for the account
}
