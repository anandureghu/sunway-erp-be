package com.erp.domain.salary;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AccountType {
    CURRENT_ACCOUNT("Current Account"),
    SAVINGS_ACCOUNT("Savings Account");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static AccountType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        String normalized = trimmed.replace("-", "_").replace(" ", "_").toUpperCase();
        for (AccountType type : values()) {
            if (type.name().equals(normalized) || type.displayName.equalsIgnoreCase(trimmed)) {
                return type;
            }
        }

        String compact = normalized.replace("_", "");
        if ("CURRENT".equals(compact) || "CURRENTACCOUNT".equals(compact)) {
            return CURRENT_ACCOUNT;
        }
        if ("SAVINGS".equals(compact) || "SAVING".equals(compact) || "SAVINGSACCOUNT".equals(compact)
                || "SAVINGACCOUNT".equals(compact)) {
            return SAVINGS_ACCOUNT;
        }

        return null;
    }
}
