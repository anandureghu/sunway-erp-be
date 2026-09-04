package com.erp.domain.enums;

public enum ContractType {
    PERMANENT,
    TEMPORARY,
    INTERN,
    CONSULTANT;

    /**
     * The contract type that matches an employment category, so the two stay in sync.
     * A "CONTRACT" (fixed-term) category maps to a TEMPORARY contract; the rest map
     * one-to-one. Returns null when the category is null.
     */
    public static ContractType fromEmploymentCategory(EmploymentCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case PERMANENT -> PERMANENT;
            case CONTRACT, TEMPORARY -> TEMPORARY;
            case INTERN -> INTERN;
            case CONSULTANT -> CONSULTANT;
        };
    }
}
