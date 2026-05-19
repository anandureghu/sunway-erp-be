package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DivisionResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String description;

    private Long managerId;
    private String managerFirstName;
    private String managerLastName;

    private Long companyId;
    private String companyName;
    private String companyCode;
}
