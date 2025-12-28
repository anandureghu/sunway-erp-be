package com.erp.domain.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_requisitions")
public class PurchaseRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requisitionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseRequisitionStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private Instant createdAt;
    private Instant approvedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "requisition_id")
    private List<PurchaseRequisitionItem> items;

    private Instant convertedAt;

    @ManyToOne
    @JoinColumn(name = "converted_by")
    private User convertedBy;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
