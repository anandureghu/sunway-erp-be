package com.erp.dto.inventory;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCreateDTO {

    private String customerName;

    private String taxId;

    private String paymentTerms;

    private String currencyCode;

    private BigDecimal creditLimit;

    private String street;
    private String city;
    private String state;
    private String country;
    private String phoneNo;
    private String email;

    private String contactPersonName;
    private String websiteUrl;
    private String customerType;
}
