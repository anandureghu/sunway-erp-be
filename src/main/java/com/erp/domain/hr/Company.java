package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

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

    /**
     * The company head (CEO / Chairperson) — an employee of this company that all
     * department managers report to. Stored as the employee id; the name is resolved
     * client-side. {@code ceoTitle} labels the role (e.g. "CEO", "Chairperson").
     */
    @Column(name = "ceo_employee_id")
    private Long ceoEmployeeId;

    @Column(name = "ceo_title", length = 60)
    private String ceoTitle;

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

    /**
     * HR policy: attendance & working hours.
     * `standardWorkingHoursPerDay` is the length of a full working day — it drives the
     * "full day worked" threshold in attendance reports and the worked-day divisor in
     * payroll. `requireCheckIn` toggles punch in/out: when false the org does not check
     * in/out and every active employee is auto-marked present for the standard day.
     */
    @Builder.Default
    @Column(name = "standard_working_hours_per_day", nullable = false, precision = 4, scale = 2)
    private BigDecimal standardWorkingHoursPerDay = new BigDecimal("6.00");

    @Builder.Default
    @Column(name = "require_check_in", nullable = false)
    private boolean requireCheckIn = true;

    /**
     * IANA timezone used for attendance punches and company-local clocks
     * (e.g. Asia/Qatar). Defaults to Qatar when unset.
     */
    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Qatar";

    /**
     * HR policy: minutes of grace after the max shift (standard + OT cap) before
     * the system automatically checks the employee out. {@code null}/{@code 0}
     * means check out at the cap with no extra grace. Typical values: 15, 20, 30.
     */
    @Column(name = "max_shift_checkout_grace_minutes")
    private Integer maxShiftCheckoutGraceMinutes;

    /**
     * HR policy: ERP UI session idle timeout in minutes. After this many minutes of
     * client inactivity the frontend signs the user out. Session security only —
     * unrelated to attendance auto check-out. {@code null}/{@code 0} = disabled (Off).
     * Allowed values: 15, 20, 30.
     */
    @Column(name = "session_idle_timeout_minutes")
    private Integer sessionIdleTimeoutMinutes;

    /**
     * HR policy: probation period (in months) applied to newly created employees.
     * New hires start {@code UNDER_PROBATION} and must be confirmed once it ends.
     * Zero disables probation — new hires are created {@code ACTIVE}.
     */
    @Builder.Default
    @Column(name = "probation_period_months", nullable = false)
    private Integer probationPeriodMonths = 3;

    /**
     * Qatar labor-law company defaults (editable in HR Policies).
     * OT multipliers are policy knobs for future overtime/payroll; night window
     * is 21:00–03:00 by statute default; OT is capped at 2 hours/day.
     */
    @Builder.Default
    @Column(name = "ot_day_rate_multiplier", nullable = false, precision = 6, scale = 2)
    private BigDecimal otDayRateMultiplier = new BigDecimal("1.25");

    @Builder.Default
    @Column(name = "ot_night_friday_holiday_rate_multiplier", nullable = false, precision = 6, scale = 2)
    private BigDecimal otNightFridayHolidayRateMultiplier = new BigDecimal("1.50");

    @Builder.Default
    @Column(name = "ot_night_start_time", nullable = false)
    private LocalTime otNightStartTime = LocalTime.of(21, 0);

    @Builder.Default
    @Column(name = "ot_night_end_time", nullable = false)
    private LocalTime otNightEndTime = LocalTime.of(3, 0);

    @Builder.Default
    @Column(name = "ot_max_hours_per_day", nullable = false, precision = 6, scale = 2)
    private BigDecimal otMaxHoursPerDay = new BigDecimal("2.00");

    @Builder.Default
    @Column(name = "minimum_monthly_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumMonthlyWage = new BigDecimal("1000.00");

    @Builder.Default
    @Column(name = "default_housing_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultHousingAllowance = new BigDecimal("500.00");

    @Builder.Default
    @Column(name = "default_food_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultFoodAllowance = new BigDecimal("300.00");

    @Builder.Default
    @Column(name = "default_transportation_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultTransportationAllowance = new BigDecimal("0.00");

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

    /** Subscription total storage quota (cloud + database) — hydrated for SUPER_ADMIN with storage usage. */
    @Transient
    private Long maxStorageBytes;

}
