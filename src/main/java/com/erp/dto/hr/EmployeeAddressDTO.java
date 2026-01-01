package com.erp.dto.hr;

import lombok.Data;

@Data
public class EmployeeAddressDTO {

    private Long id; // used for update
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private boolean primaryAddress;
}
