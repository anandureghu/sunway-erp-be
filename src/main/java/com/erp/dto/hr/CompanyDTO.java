package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO {
    private String companyName;
    private String noOfEmployees;
    private Long currencyId;
    private Long crNo;
    private String computerCard;
    private String street;
    private String city;
    private String state;
    private String country;
    private String phoneNo;
    private String createdBy;
    private boolean hrEnabled;
    private boolean financeEnabled;
    private boolean inventoryEnabled;
}
