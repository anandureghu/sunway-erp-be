package com.erp.domain.hr;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An exit interview for a departing employee (status RESIGNED / TERMINATED / RETIRED).
 * The rich, multi-section questionnaire is stored as a JSON document in {@link #responses}
 * so the form can evolve without schema changes; a few key fields are promoted to columns
 * for querying and display. One record per employee.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "employee_exit_interview",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exit_interview_employee",
                columnNames = "employee_id"))
public class EmployeeExitInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** Resignation / Termination / End of Contract / Retirement / Mutual Agreement. */
    @Column(name = "separation_type", length = 40)
    private String separationType;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "primary_reason", length = 160)
    private String primaryReason;

    /** DRAFT while being filled, SUBMITTED once finalised. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "DRAFT";

    /** The full questionnaire (all sections) as a JSON object of field key → value. */
    @Column(name = "responses", columnDefinition = "LONGTEXT")
    private String responses;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
