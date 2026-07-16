package com.erp.domain.sales;

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
@Table(
        name = "picklists",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_picklists_company_picklist_number", columnNames = {"company_id", "picklist_number"})
        }
)
public class Picklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String picklistNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Column(nullable = false)
    private String status; // CREATED, PICKED, CANCELLED

    @Builder.Default
    private boolean archived = false;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "picklist_id")
    private List<PicklistItem> items;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
