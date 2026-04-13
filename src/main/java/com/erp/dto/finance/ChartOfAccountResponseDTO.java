package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ChartOfAccountResponseDTO {
    private Long id;
    private String accountNo;
    private String accountCode;
    private String accountName;
    private String description;
    private String type;
    private BigDecimal balance;
    private Long companyId;

    private Long parentId;
    private String parentName;
    private String parentType;
    private String parentCode;
    private String parentAccountNo;
    private String interCompanyNumber;
    private Instant asOfDate;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;

    private Long departmentId;
    private String departmentName;
    private String departmentCode;

    private String projectCode;
    private boolean active;
    private boolean initialBalanceSet;
}
