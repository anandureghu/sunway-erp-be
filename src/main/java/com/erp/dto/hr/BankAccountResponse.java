package com.erp.dto.hr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BankAccountResponse {

    private Long id;
    private Long companyId;

    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String branchName;
    private String accountHolderName;
    private Boolean primaryAccount;
}

