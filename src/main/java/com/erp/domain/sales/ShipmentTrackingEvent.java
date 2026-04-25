package com.erp.domain.sales;

import com.erp.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "shipment_tracking_events")
public class ShipmentTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @Column(nullable = false)
    private String status;

    private String location;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private Instant eventAt;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (eventAt == null) {
            eventAt = Instant.now();
        }
        createdAt = Instant.now();
    }
}
