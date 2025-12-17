package com.erp.domain.sales;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
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
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String shipmentNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "picklist_id")
    private Picklist picklist;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String status; // CREATED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED

    private String carrierName;
    private String trackingNumber;

    private Instant dispatchedAt;
    private Instant deliveredAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "shipment_id")
    private List<ShipmentItem> items;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
