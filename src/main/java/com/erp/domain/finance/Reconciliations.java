package com.erp.domain.finance;

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
@Table(name = "reconciliations")
public class Reconciliations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reconcileAccount;
    private Instant asOfDate;

    @Column(precision = 18, scale = 2)
    private BigDecimal endingBalance;

    private Instant createdAt;
}
