package com.erp.dto.immigration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PassportRequestDTO {

    private Long employeeId;

    @NotBlank(message = "Passport number is required")
    private String passportNo;

    @NotBlank(message = "Name as passport is required")
    private String nameAsPassport;

    @NotBlank(message = "Issue country is required")
    private String issueCountry;

    @NotBlank(message = "Nationality is required")
    private String nationality;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;
}
