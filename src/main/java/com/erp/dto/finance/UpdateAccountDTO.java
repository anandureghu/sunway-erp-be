package com.erp.dto.finance;

import lombok.Data;

@Data
public class UpdateAccountDTO {
    private String accountName;
    private String description;
    private String status;
    private String glAccountClassTypeKey;
    private String glAccountType;
}
