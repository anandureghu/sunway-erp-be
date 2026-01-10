package com.erp.util;

import com.erp.domain.hr.Company;

public class EmployeeUserUtil {

    private EmployeeUserUtil() {}

    public static String generateUsername(String firstName, String lastName) {
        return (firstName.substring(0, 1) + "." + lastName).toLowerCase();
    }

    public static String generateEmail(String username, Company company) {
        return username + "@" + company.getCompanyName().toLowerCase() + ".com";
    }

    public static String generateDefaultPassword(String username) {
        return username + "123@$";
    }
}
