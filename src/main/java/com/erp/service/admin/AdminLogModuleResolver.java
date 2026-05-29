package com.erp.service.admin;

/**
 * Maps request paths and logger names to a human-readable ERP module label.
 */
public final class AdminLogModuleResolver {

    private AdminLogModuleResolver() {
    }

    public static String resolve(String requestUri, String loggerName) {
        String fromUri = resolveFromUri(requestUri);
        if (fromUri != null) {
            return fromUri;
        }
        return resolveFromLogger(loggerName);
    }

    private static String resolveFromUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }
        String path = requestUri.toLowerCase();
        if (path.contains("/finance/") || path.contains("/invoices") || path.contains("/payments")) {
            return "Finance";
        }
        if (path.contains("/purchase/")) {
            return "Purchase";
        }
        if (path.contains("/sales/")) {
            return "Sales";
        }
        if (path.contains("/inventory/") || path.contains("/items") || path.contains("/warehouses")) {
            return "Inventory";
        }
        if (path.contains("/employees") || path.contains("/leave") || path.contains("/payroll")
                || path.contains("/appraisal") || path.contains("/salary")) {
            return "HR";
        }
        if (path.contains("/auth/")) {
            return "Auth";
        }
        if (path.contains("/admin/")) {
            return "Admin";
        }
        if (path.contains("/vendors") || path.contains("/customers")) {
            return "Master data";
        }
        return null;
    }

    private static String resolveFromLogger(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return "System";
        }
        String name = loggerName.toLowerCase();
        if (name.contains(".finance.")) {
            return "Finance";
        }
        if (name.contains(".purchase.")) {
            return "Purchase";
        }
        if (name.contains(".sales.")) {
            return "Sales";
        }
        if (name.contains(".inventory.")) {
            return "Inventory";
        }
        if (name.contains(".hr.") || name.contains(".salary.") || name.contains(".appraisal.")
                || name.contains(".employee")) {
            return "HR";
        }
        if (name.contains(".security.") || name.contains(".auth")) {
            return "Auth";
        }
        if (name.contains(".admin.")) {
            return "Admin";
        }
        return "System";
    }
}
