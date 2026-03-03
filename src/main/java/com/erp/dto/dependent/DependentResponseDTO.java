package com.erp.dto.dependent;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DependentResponseDTO {

    private Long id;
    private Long employeeId;

    private String firstName;
    private String middleName;
    private String lastName;

    private LocalDate dateOfBirth;
    private String gender;

    private String nationalId;
    private String nationality;

    private String maritalStatus;
    private String relationship;

    // ✅ Contact Information

    private String phoneNumber;

    private String addressLine1;
    private String addressLine2;

    private String city;
    private String state;
    private String postalCode;
    private String country;
}