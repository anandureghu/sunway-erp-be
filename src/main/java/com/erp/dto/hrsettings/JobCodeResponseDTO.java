package com.erp.dto.hrsettings;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobCodeResponseDTO {

    private Long id;
    private String code;
    private String title;
    private String level;
    private String grade;
    private Boolean active;
    private Long companyId;
}