package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String companyCode;
    private String street;
    private String city;
    private String state;
    private Long taxRate;
    private boolean isTaxActive;
    private String country;
    private String phoneNo;
    private String companyEmail;
    private String billingEmail;
    private String websiteUrl;
    private String logoUrl;
    private String createdBy;
    private boolean hrEnabled;
    private boolean financeEnabled;
    private boolean inventoryEnabled;

    private String invoiceHeaderSubtitle;
    private String invoiceNotesUnpaid;
    private String invoiceNotesPaid;
    private String invoiceTerms;
    private String invoiceFooterCompanyLine;
    private String invoiceFooterTaxLine;
    private String invoiceFooterSignatureNote;
    private String invoiceFooterSupportEmail;
    private String invoiceFooterBillingEmail;
    private Boolean invoiceQrEnabled;

    // ---- Leave accrual policy ----
    private Boolean annualLeaveAccrualEnabled;
    private BigDecimal annualLeaveAccrualDaysPerMonth;
    private Integer minServiceMonthsForAnnualLeave;

    // ---- Retirement / end-of-service compensation policy ----
    private Boolean retirementCompensationEnabled;
    private BigDecimal retirementCompensationMonthsPerYear;
}
