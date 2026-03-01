package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "allowance_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllowanceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;  // BASIC, HRA, BONUS

    private String description;

    @Column(nullable = false)
    private boolean active = true;
}