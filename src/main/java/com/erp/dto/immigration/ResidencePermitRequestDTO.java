package com.erp.dto.immigration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ResidencePermitRequestDTO {

    private Long employeeId;
    @NotBlank(message = "Permit ID number is required")
    private String permitIdNumber;

    @NotBlank(message = "Visa type is required")
    private String visaType;

    @NotBlank(message = "Duration type is required")
    private String durationType;

    @NotBlank(message = "Visa duration is required")
    private String visaDuration;

    @NotBlank(message = "Nationality is required")
    private String nationality;

    @NotBlank(message = "Occupation is required")
    private String occupation;

    @NotBlank(message = "Issue place is required")
    private String issuePlace;

    @NotBlank(message = "Issue authority is required")
    private String issueAuthority;

    @NotBlank(message = "Visa status is required")
    private String visaStatus;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
