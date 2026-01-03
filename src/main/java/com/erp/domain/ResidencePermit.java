package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employee_residence_permits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResidencePermit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    private String visaType;
    private String durationType;
    private String visaDuration;

    private String nationality;
    private String occupation;
    private String issuePlace;
    private String issueAuthority;
    private String visaStatus;

    private LocalDate startDate;
    private LocalDate endDate;
}
