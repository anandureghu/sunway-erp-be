package com.erp.domain.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long orderId;

    private String orderName;
    private String orderStatus;
    private String createdBy;
    private String agreement;
    private Instant estimatedDeliveryDate;
    private Instant shipmentDate;
    private Instant orderDate;
    private String notesRemarks;

    @ManyToOne
    @JoinColumn(name = "supplier")
    private Vendor supplier;

    private Instant createdAt;
}
