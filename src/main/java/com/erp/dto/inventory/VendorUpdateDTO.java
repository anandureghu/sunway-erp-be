package com.erp.dto.inventory;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorUpdateDTO {

    private String vendorName;
    private String taxId;
    private String paymentTerms;
    private String currencyCode;
    private BigDecimal creditLimit;

    private Boolean is1099Vendor;
    private Boolean isActive;

    private String street;
    private String city;
    private String country;
    private String phoneNo;
    private String email;

    private String contactPersonName;
    private String fax;
    private String websiteUrl;
    
    private String remarks;
}
