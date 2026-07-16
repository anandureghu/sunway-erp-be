package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", length = 50, nullable = false)
    private String companyName;

    @Column(name = "no_of_employees", length = 20)
    private String noOfEmployees;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency")
    private Currency currency;

    @Column(name = "cr_no")
    private Long crNo;

    @Column(name = "computer_card", length = 20)
    private String computerCard;

    @Column(length = 50)
    private String street;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 50)
    private String country;

    @Column(name = "phone_no", length = 20)
    private String phoneNo;

    @Column(name = "company_email", length = 120)
    private String companyEmail;

    @Column(name = "billing_email", length = 120)
    private String billingEmail;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;


    @Column(name = "company_code", length = 3)
    private String companyCode;

    @Column(name = "industry", length = 100)
    private String industry;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "tax_rate", length = 3)
    private String taxRate;

    @Column(name = "is_tax_active", nullable = false)
    private boolean isTaxActive;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "is_hr_enabled", nullable = false)
    private boolean hrEnabled;

    @Column(name = "is_finance_enabled", nullable = false)
    private boolean financeEnabled;

    @Column(name = "is_inventory_enabled", nullable = false)
    private boolean inventoryEnabled;

    /** Chart of accounts id; must belong to this company. Used for sales orders and sales invoices. */
    @Column(name = "default_sales_debit_account_id")
    private Long defaultSalesDebitAccountId;

    @Column(name = "default_sales_credit_account_id")
    private Long defaultSalesCreditAccountId;

    /** Used for purchase requisitions. */
    @Column(name = "default_purchase_debit_account_id")
    private Long defaultPurchaseDebitAccountId;

    @Column(name = "default_purchase_credit_account_id")
    private Long defaultPurchaseCreditAccountId;

    /** Default bank for sales orders and sales invoices. */
    @Column(name = "default_bank_account_id")
    private Long defaultBankAccountId;

    /** Qatar bank payroll file (SIF): employer / payer IDs and payer bank (CSV export). */
    @Column(name = "payroll_employer_eid", length = 64)
    private String payrollEmployerEid;

    @Column(name = "payroll_payer_eid", length = 64)
    private String payrollPayerEid;

    @Column(name = "payroll_payer_qid", length = 64)
    private String payrollPayerQid;

    @Column(name = "payroll_payer_bank_short_name", length = 32)
    private String payrollPayerBankShortName;

    @Column(name = "payroll_payer_iban", length = 64)
    private String payrollPayerIban;

    @Column(name = "payroll_sif_version", length = 16)
    private String payrollSifVersion;

    /**
     * HR policy: annual leave accrual.
     * When enabled, annual-leave balance accrues as months_worked * accrual_days_per_month,
     * and leave can only be taken after `minServiceMonthsForAnnualLeave` months of service.
     */
    @Column(name = "annual_leave_accrual_enabled", nullable = false)
    private boolean annualLeaveAccrualEnabled;

    @Builder.Default
    @Column(name = "annual_leave_accrual_days_per_month", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualLeaveAccrualDaysPerMonth = new BigDecimal("1.50");

    @Builder.Default
    @Column(name = "min_service_months_for_annual_leave", nullable = false)
    private Integer minServiceMonthsForAnnualLeave = 6;

    /**
     * HR policy: retirement / end-of-service compensation.
     * When enabled, employee accrues `retirementCompensationMonthsPerYear` months of basic salary
     * per completed year of service.
     */
    @Column(name = "retirement_compensation_enabled", nullable = false)
    private boolean retirementCompensationEnabled;

    @Builder.Default
    @Column(name = "retirement_compensation_months_per_year", nullable = false, precision = 5, scale = 2)
    private BigDecimal retirementCompensationMonthsPerYear = new BigDecimal("1.00");

    /**
     * HR policy: loan eligibility & repayment.
     * When enabled, an employee may request a loan only after `loanMinServiceDays`
     * of service (since join date), and the repayment period may not exceed
     * `loanMaxRepaymentMonths`.
     */
    @Column(name = "loan_policy_enabled", nullable = false)
    private boolean loanPolicyEnabled;

    @Builder.Default
    @Column(name = "loan_min_service_days", nullable = false)
    private Integer loanMinServiceDays = 365;

    @Builder.Default
    @Column(name = "loan_max_repayment_months", nullable = false)
    private Integer loanMaxRepaymentMonths = 24;

    @Transient
    private String invoiceHeaderSubtitle;

    @Transient
    private String invoiceNotesUnpaid;

    @Transient
    private String invoiceNotesPaid;

    @Transient
    private String invoiceTerms;

    @Transient
    private String invoiceFooterCompanyLine;

    @Transient
    private String invoiceFooterTaxLine;

    @Transient
    private String invoiceFooterSignatureNote;

    @Transient
    private String invoiceFooterSupportEmail;

    @Transient
    private String invoiceFooterBillingEmail;

    @Transient
    private boolean invoiceQrEnabled;

    /** Live count of ACTIVE employees, computed on read — not persisted. */
    @Transient
    private Long employeeCount;

    /** Storage usage — hydrated for SUPER_ADMIN only. See CompanyStorageService. */
    @Transient
    private Long cloudStorageBytes;

    @Transient
    private Long databaseStorageBytes;

    @Transient
    private Instant storageCalculatedAt;

}
