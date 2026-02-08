package com.erp.dto.immigration;

import jdk.jshell.Snippet;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class ResidencePermitResponseDTO {

    private Long id;

    private Long employeeId;
    private String employeeCode;
    private String employeeName;

    private String permitIdNumber;

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
