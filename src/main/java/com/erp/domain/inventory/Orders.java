package com.erp.domain.inventory;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_company_order_id", columnNames = {"company_id", "order_id"})
        }
)
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private Instant createdAt;
}
