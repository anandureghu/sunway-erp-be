package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "employee_residence_permits",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "employee_id"),
                @UniqueConstraint(name = "uk_residence_permits_company_permit_id_number", columnNames = {"company_id", "permit_id_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResidencePermit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "permit_id_number", nullable = false, length = 50)
    private String permitIdNumber;
    @Column(nullable = false)
    private String visaType;
    @Column(nullable = false)
    private String durationType;
    @Column(nullable = false)
    private String visaDuration;
    @Column(nullable = false)
    private String nationality;
    @Column(nullable = false)
    private String occupation;
    private String issuePlace;
    private String issueAuthority;
    private String visaStatus;
    private LocalDate startDate;
    private LocalDate endDate;

    /** Blob path of the uploaded residence-permit / visa scan (null if none). */
    @Column(name = "document_path", length = 512)
    private String documentPath;
}
