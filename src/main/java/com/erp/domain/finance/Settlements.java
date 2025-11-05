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
@Table(name = "settlements")
public class Settlements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String settlementType;
    private String paymentMethod;
    private String settlementAccount;

    private Instant transactionDate;
    private BigDecimal amount;
    private Instant settlementDate;
    private String settlementCode;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalDue;

    private String debitAccount;
}
