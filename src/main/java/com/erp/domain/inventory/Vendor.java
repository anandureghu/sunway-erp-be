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
@Table(name = "vendor")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vendorId;
    private String vendorName;
    private String street;
    private String city;
    private String country;
    private String phoneNo;
    private String email;

    private Instant createdAt;
}
