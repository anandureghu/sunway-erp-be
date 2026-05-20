package com.erp.domain.hrsettings;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @Column(name = "salary_grade", nullable = false)
    private String salaryGrade;  // G1, G2...

    @Column(name = "min_salary", precision = 15, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 15, scale = 2)
    private BigDecimal maxSalary;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
