package com.erp.domain.appraisal;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "appraisal_config")
public class AppraisalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning company. NULL means a legacy/global config that acts as a shared
     * fallback for companies that have not created their own.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    private Integer year;
    private String cycleName;
    private String status; // DRAFT, ACTIVE, CLOSED

    /* ── Period ─────────────────────────────────── */

    @Column(nullable = false)
    private String startMonth;

    @Column(nullable = false)
    private String endMonth;

    /* ── Goals limits ───────────────────────────── */

    private Integer minGoals;  // ✅ proper field
    private Integer maxGoals;  // ✅ proper field

    /* ── Flags ──────────────────────────────────── */

    private Boolean enableSelfAssessment;
    private Boolean enableMidYear;
    private Boolean enablePIP;

    /* ── Roles ──────────────────────────────────── */

    @OneToMany(
            mappedBy = "config",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<AppraisalRoleConfig> roleConfigs = new ArrayList<>();

    /* ── Audit ──────────────────────────────────── */

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @PrePersist
    public void prePersist() {
        createdDate = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedDate = LocalDateTime.now();
    }
}