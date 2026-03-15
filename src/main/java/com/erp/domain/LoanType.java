package com.erp.domain;

public enum LoanType {
    CAR_LOAN("CAR"),
    PERSONAL_LOAN("PER"),
    HOUSING_LOAN("HOU"),
    EDUCATION_LOAN("EDU"),
    MEDICAL_LOAN("MED");

    private final String prefix;

    LoanType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDisplayName() {
        return this.name().replace("_", " ");
    }

    public String getName() {
        return getDisplayName();
    }
}