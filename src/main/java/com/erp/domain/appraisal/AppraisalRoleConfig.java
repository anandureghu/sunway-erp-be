package com.erp.domain.appraisal;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "appraisal_role_config")
public class AppraisalRoleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The job code this KPI set applies to (e.g. "ENG-003"). Appraisal configs
     * are keyed by job code; persisted in the legacy {@code role_name} column so
     * no destructive rename of a live table is required.
     */
    @Column(name = "role_name")
    private String jobCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private AppraisalConfig config;

    @OneToMany(
            mappedBy = "roleConfig",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AppraisalGoalTemplate> goalTemplates = new ArrayList<>();
}