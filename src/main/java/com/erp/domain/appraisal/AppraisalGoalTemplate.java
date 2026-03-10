package com.erp.domain.appraisal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "appraisal_goal_template")
public class AppraisalGoalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kpi;

    private String description;

    private Integer weight;

    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_config_id")
    private AppraisalRoleConfig roleConfig;
}