package com.erp.domain.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales")
public class Sales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemCode;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal discount;
    private BigDecimal unitPrice;
    private BigDecimal total;
    private BigDecimal totalDue;

    @ManyToOne
    @JoinColumn(name = "order_no")
    private Orders order;

    private Instant createdAt;
}
