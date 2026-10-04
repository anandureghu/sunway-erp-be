package com.erp.dto.salary;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Outcome of granting one benefit to a group of employees. */
@Getter
@Builder
public class BenefitGrantBulkResultDTO {
    /** Employees selected by the scope. */
    private int matched;
    /** Grants created. */
    private int created;
    /** Employees skipped, with the reason (e.g. annual ticket already granted this year). */
    private List<Skipped> skipped;

    @Getter
    @Builder
    public static class Skipped {
        private Long employeeId;
        private String employeeName;
        private String reason;
    }
}
