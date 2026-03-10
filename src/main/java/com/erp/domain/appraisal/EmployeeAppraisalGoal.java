package com.erp.domain.appraisal;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Entity
@Table(
        name = "employee_appraisal_goals",
        indexes = {
                @Index(name = "idx_appraisal_goal_appraisal", columnList = "appraisal_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAppraisalGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= RELATIONSHIP ================= */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appraisal_id", nullable = false)
    private EmployeeAppraisal appraisal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private AppraisalGoalTemplate template;

    /* ================= SNAPSHOT DATA ================= */

    private String kpi;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer weight;

    /* ================= RATINGS ================= */

    @Min(1)
    @Max(5)
    private Integer selfRating;

    @Min(1)
    @Max(5)
    private Integer managerRating;

    @Column(columnDefinition = "TEXT")
    private String selfComment;

    @Column(columnDefinition = "TEXT")
    private String managerComment;
}