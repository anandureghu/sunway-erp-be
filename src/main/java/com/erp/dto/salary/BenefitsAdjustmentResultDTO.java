package com.erp.dto.salary;

import java.util.List;

/**
 * Outcome of a bulk benefits adjustment: how many employees were matched, how many
 * actually had a compensation record updated, and the names of those updated.
 */
public record BenefitsAdjustmentResultDTO(
        int matched,
        int adjusted,
        List<String> adjustedEmployees) {
}
