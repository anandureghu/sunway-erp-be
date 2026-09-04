package com.erp.util;

import com.erp.domain.hr.Company;

public class EmployeeUserUtil {

    private EmployeeUserUtil() {}

    /**
     * Email-safe login/local part in the form {@code [first initial].[last name]}
     * (e.g. "j.doe"), lowercase with spaces and punctuation stripped from each part.
     * Falls back gracefully when a name part is missing.
     */
    public static String generateUsername(String firstName, String lastName) {
        String first = normalize(firstName);
        String last = normalize(lastName);
        String initial = first.isEmpty() ? "" : first.substring(0, 1);

        if (initial.isEmpty() && last.isEmpty()) {
            return "user";
        }
        if (initial.isEmpty()) {
            return last;
        }
        if (last.isEmpty()) {
            return initial;
        }
        return initial + "." + last;
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
