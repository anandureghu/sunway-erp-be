package com.erp.domain.security;

/**
 * Application-wide permission modules (HR, inventory, finance, etc.).
 * Stored as STRING in JPA; DB enum values match these constant names.
 */
public enum AppModule {
    EMPLOYEE_PROFILE,
    CURRENT_JOB,
    SALARY,
    PAYROLL,
    LEAVES,
    LOANS,
    DEPENDENTS,
    APPRAISAL,
    IMMIGRATION,
    HR_REPORTS,
    HR_SETTINGS,
    INVENTORY_CATEGORY,
    INVENTORY_WAREHOUSE,
    INVENTORY_STOCK,
    INVENTORY_ITEM,
    INVENTORY_PURCHASE,
    INVENTORY_RECEIPT,
    INVENTORY_SALES,
    FINANCE_COA,
    FINANCE_JOURNAL,
    FINANCE_LEDGER,
    FINANCE_INVOICE,
    FINANCE_PAYMENT,
    FINANCE_BUDGET,
    FINANCE_RECONCILIATION,
    FINANCE_REPORTS
}
