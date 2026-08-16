package com.erp.dto.hr;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Exit-interview payload. {@link #responses} carries the full multi-section
 * questionnaire as a free-form JSON object (field key → value); the promoted
 * fields mirror the columns for querying/display. On the response the read-only
 * employee block (name, department, join date, …) pre-fills Section 1.
 */
@Data
public class ExitInterviewDTO {

    // ── read-only context (server-populated on GET, ignored on save) ──
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    private String reportingManager;
    private String nationality;
    private String employeeStatus;

    // ── promoted, editable fields ──
    private String separationType;
    private LocalDate lastWorkingDay;
    private String primaryReason;
    private String status;               // DRAFT | SUBMITTED

    /** The full questionnaire (all sections) as JSON. */
    private Map<String, Object> responses;

    private Instant submittedAt;
    private Instant updatedAt;
    private boolean exists;
}
