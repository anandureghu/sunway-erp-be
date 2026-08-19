package com.erp.util;

import com.erp.domain.hr.Company;

public class EmployeeUserUtil {

    private EmployeeUserUtil() {}

    /**
     * Email-safe login/local part built from the employee's name — lowercase with
     * every space and special character stripped, exactly how the company domain is
     * built.
     */
    public static String generateUsername(String firstName, String lastName) {
        String local = normalize(firstName) + normalize(lastName);
        return local.isEmpty() ? "user" : local;
    }

    public static String generateEmail(String username, Company company) {
        String domain = normalize(company.getCompanyName());
        return username + "@" + domain + ".com";
    }

    /** Lowercase and drop anything that isn't a letter or digit (spaces, dots, punctuation). */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static String generateDefaultPassword(String username) {
        return username + "123@$";
    }
}
