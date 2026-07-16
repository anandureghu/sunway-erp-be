package com.erp.util;

import java.util.Locale;
import java.util.Set;

public final class ExpenseCategoryLabels {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "RENT", "EMPLOYEE_REIMBURSEMENT", "VENDOR_REIMBURSEMENT", "UTILITIES", "OTHER");

    private ExpenseCategoryLabels() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Expense category is required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!ALLOWED_CATEGORIES.contains(code)) {
            throw new IllegalArgumentException(
                    "Invalid expense category. Use one of: rent, employee reimbursement, "
                            + "vendor reimbursement, utilities, other.");
        }
        return code;
    }

    public static String displayLabel(String code) {
        if (code == null || code.isBlank()) {
            return "—";
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "RENT" -> "Rent";
            case "EMPLOYEE_REIMBURSEMENT" -> "Employee reimbursement";
            case "VENDOR_REIMBURSEMENT" -> "Vendor reimbursement";
            case "UTILITIES" -> "Utilities";
            case "OTHER" -> "Other";
            default -> code.replace('_', ' ');
        };
    }
}
