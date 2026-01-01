package com.erp.dto.immigration;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ResidencePermitResponseDTO {

    private Long id;
    private Long employeeId;

    private String visaType;
    private String durationType;
    private String visaDuration;

    private String nationality;
    private String occupation;
    private String issuePlace;
    private String issueAuthority;
    private String visaStatus;

    private LocalDate startDate;
    private LocalDate endDate;
}
