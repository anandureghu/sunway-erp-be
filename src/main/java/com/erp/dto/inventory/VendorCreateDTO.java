package com.erp.dto.inventory;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorCreateDTO {

    private String vendorName;
    private String vendorCode;
    private String taxId;
    private Long categoryId;
    private String vendorCrNo;
    private String bankName;
    private String iban;
    private String paymentTerms;
    private String currencyCode;
    private BigDecimal creditLimit;

    private boolean is1099Vendor;
    private boolean isActive;

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
