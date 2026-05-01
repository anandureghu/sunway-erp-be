package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

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


    @Column(name = "company_code", length = 3)
    private String companyCode;

    @Column(name = "tax_rate", length = 3)
    private String taxRate;

    @Column(name = "is_tax_active", nullable = false)
    private boolean isTaxActive;

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

}
