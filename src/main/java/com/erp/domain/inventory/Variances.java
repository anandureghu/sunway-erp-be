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
@Table(name = "variances")
public class Variances {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemCode;
    private String varianceStatus;
    private Instant varianceDate;
    private String fromLocation;
    private String toLocation;

    private BigDecimal varianceQuantity;
    private String varianceReason;
    private String varianceType;
    private String notesRemarks;

    private Instant createdAt;
}
