package com.erp.dto.hr;

import com.erp.domain.enums.ContractStatus;
import com.erp.domain.enums.ContractType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ContractResponseDTO {

    private Long id;
    private String contractCode;

    private ContractType contractType;
    private ContractStatus status;

    private LocalDate effectiveDate;
    private LocalDate expirationDate;

    private Integer contractPeriodMonths;
    private Integer noticePeriodDays;
    private String salaryRateType;

    private LocalDate signatureDate;
    private String signedBy;
    private String termsAndConditions;
    private String attachmentUrl;

    private Long employeeId;
    private String staffName;               // 🔥 ADD

    private List<AllowanceResponseDTO> allowances;
}