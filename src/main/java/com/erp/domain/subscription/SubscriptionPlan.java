package com.erp.domain.subscription;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "default_billing_days")
    private Integer defaultBillingDays;

    @Column(name = "default_amount", precision = 19, scale = 4)
    private BigDecimal defaultAmount;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
