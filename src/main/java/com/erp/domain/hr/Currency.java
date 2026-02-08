package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "currency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String countryName;

    @Column(nullable = false)
    private String currencyName;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 5)
    private String currencySymbol;
}

