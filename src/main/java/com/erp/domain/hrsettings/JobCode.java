package com.erp.domain.hrsettings;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "job_codes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_code_company_code",
                columnNames = {"company_id", "code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;   // ENG-003

    @Column(nullable = false)
    private String title;  // Software Engineer

    @Column(nullable = false)
    private String level;  // Intern, Junior, Mid...

    @Column(nullable = false)
    private String grade;  // G1, G2...

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}