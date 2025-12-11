package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ChartOfAccountResponseDTO {

    private Long id;
    private String accountCode;
    private String accountName;
    private String description;
    private String type;
    private Long parentId;

    private String currency;
    private String status;

    private String glAccountClassTypeKey;
    private String glAccountType;

    private BigDecimal balance;
    private Long companyId;
}
