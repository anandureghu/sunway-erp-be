package com.erp.dto.salary;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BankDetailsRequestDTO {

    private String bankName;
    /** E.g. QIB, CBQ — required for bank payroll CSV export */
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
