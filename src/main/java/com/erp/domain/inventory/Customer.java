package com.erp.domain.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String customerId;
    private String street;
    private String city;
    private String country;
    private String phoneNo;
    private String email;

    private Instant createdAt;
}
