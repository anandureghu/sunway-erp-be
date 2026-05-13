package com.erp.util;

import com.erp.domain.hr.Company;

public class EmployeeUserUtil {

    private EmployeeUserUtil() {}

    public static String generateUsername(String firstName, String lastName) {
        return (firstName.substring(0, 1) + "." + lastName).toLowerCase();
    }

    public static String generateEmail(String username, Company company) {
        String domain = company.getCompanyName().toLowerCase().replaceAll("\\s+", "");
        return username + "@" + domain + ".com";
    }

    public static String generateDefaultPassword(String username) {
        return username + "123@$";
    }
}
