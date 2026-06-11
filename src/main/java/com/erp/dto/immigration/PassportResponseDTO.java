package com.erp.dto.immigration;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PassportResponseDTO {

    private Long id;
    private Long employeeId;

    private String passportNo;
    private String nameAsPassport;
    private String issueCountry;
    private String nationality;
    private LocalDate issueDate;
    private LocalDate expiryDate;

    /** Time-limited download URL for the uploaded scan (null if none). */
    private String documentUrl;
}
