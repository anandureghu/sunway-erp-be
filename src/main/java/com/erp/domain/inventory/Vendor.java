package com.erp.domain.inventory;

import com.erp.domain.hr.Company;
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
@Table(name = "vendor",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"company_id", "vendor_name"}),
                @UniqueConstraint(name = "uk_vendor_company_tax_id", columnNames = {"company_id", "tax_id"}),
                @UniqueConstraint(name = "uk_vendor_company_code", columnNames = {"company_id", "vendor_code"})
        })
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_code", nullable = false, length = 50)
    private String vendorCode;

    @Column(nullable = false, length = 150)
    private String vendorName;

    @Column(length = 50)
    private String taxId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "vendor_cr_no", length = 100)
    private String vendorCrNo;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(length = 64)
    private String iban;

    @Column(nullable = false, length = 50)
    private String paymentTerms;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    private BigDecimal creditLimit = BigDecimal.ZERO;

    private boolean is1099Vendor = false;

    private boolean isActive = true;

    private boolean approved = false;
    private boolean rejected = false;

    private Instant createdAt;
    private Instant updatedAt;

    @Column(length = 150)
    private String street;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(length = 30)
    private String phoneNo;

    @Column(length = 120)
    private String email;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "contact_person_name", length = 120)
    private String contactPersonName;

    @Column(length = 50)
    private String fax;

    @Column(name = "website_url", length = 200)
    private String websiteUrl;
    
    private String remarks;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
