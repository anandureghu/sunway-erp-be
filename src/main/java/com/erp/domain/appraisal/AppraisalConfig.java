package com.erp.domain.appraisal;

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

    private Integer year;

    private String cycleName;

    private String status; // DRAFT, ACTIVE, CLOSED

    /* ================= PERIOD ================= */

    @Column(nullable = false)
    private String startMonth;

    @Column(nullable = false)
    private String endMonth;

    /* ================= FLAGS ================= */

    private Boolean enableSelfAssessment;
    private Boolean enableMidYear;
    private Boolean enablePIP;

    /* ================= ROLES ================= */

    @OneToMany(
            mappedBy = "config",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AppraisalRoleConfig> roleConfigs = new ArrayList<>();

    /* ================= AUDIT ================= */

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