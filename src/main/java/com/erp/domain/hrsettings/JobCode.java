package com.erp.domain.hrsettings;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;   // ENG-003

    @Column(nullable = false)
    private String title;  // Software Engineer

    @Column(nullable = false)
    private String level;  // Intern, Junior, Mid...

    @Column(nullable = false)
    private String grade;  // G1, G2...

    @Column(nullable = false)
    private Boolean active = true;
}