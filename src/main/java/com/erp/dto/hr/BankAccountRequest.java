package com.erp.dto.hr;

import lombok.Data;

@Data
public class BankAccountRequest {
    private Long companyId;
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String branchName;
    private String accountHolderName;
    private Boolean primaryAccount;
}
