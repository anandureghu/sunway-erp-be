package com.erp.dto.salary;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankDetailsResponseDTO {

    private String bankName;
    private String bankShortName;
    private String bankBranch;
    private String accountType;
    private String accountNo;
    private String iban;

    private String country;
    private String state;
    private String city;

    private String remarks;
}

