package com.erp.domain.inventory;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "crm_enquiries")
public class CrmEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "prospect_type", nullable = false, length = 20)
    private CrmProspectType prospectType = CrmProspectType.CUSTOMER;

    @Column(length = 255)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "secondary_contact", length = 200)
    private String secondaryContact;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String source;

    @Column(length = 200)
    private String interest;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_status", nullable = false, length = 20)
    private CrmPipelineStatus pipelineStatus = CrmPipelineStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_stage", nullable = false, length = 20)
    private CrmRelationshipStage relationshipStage = CrmRelationshipStage.PROSPECTIVE;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "vendor_id")
    private Long vendorId;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<CrmEnquiryNote> notes = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (prospectType == null) prospectType = CrmProspectType.CUSTOMER;
        if (pipelineStatus == null) pipelineStatus = CrmPipelineStatus.NEW;
        if (relationshipStage == null) relationshipStage = CrmRelationshipStage.PROSPECTIVE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
