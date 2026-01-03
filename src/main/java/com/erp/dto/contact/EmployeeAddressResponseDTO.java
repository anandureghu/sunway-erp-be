package com.erp.dto.contact;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAddressResponseDTO {

    private Long id;

    private String line1;
    private String line2;
    private String city;
    private String state;
    private String country;
    private String postalCode;

    private String addressType;
    private Boolean primaryAddress;
}
