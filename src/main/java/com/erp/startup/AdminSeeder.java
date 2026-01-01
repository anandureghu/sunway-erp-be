package com.erp.startup;

import com.erp.domain.Employee;
import com.erp.domain.Role;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.service.finance.ChartOfAccountsService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    CommandLineRunner seedAdmin(
            UserRepository userRepo,
            ChartOfAccountsService chartOfAccountsService,
            CompanyRepository companyRepo,
            EmployeeRepository employeeRepo,
            PasswordEncoder encoder
    ) {
        return args -> {

            if (userRepo.count() == 0) {

                // 1. Create SUPER ADMIN user
                User admin = new User();
                admin.setFullName("Admin User");
                admin.setEmail("admin@hr.local");
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                admin.setRole(Role.SUPER_ADMIN);
                admin = userRepo.save(admin);

                // 2. Create default company
                Company company = Company.builder()
                        .companyName("Default Admin Company")
                        .noOfEmployees("10")
                        .crNo(10001L)
                        .computerCard("ADM-001")
                        .street("HQ Street")
                        .city("Doha")
                        .state("Doha")
                        .country("Qatar")
                        .phoneNo("+97412345678")
                        .createdBy(admin.getUsername())
                        .hrEnabled(true)
                        .financeEnabled(true)
                        .inventoryEnabled(true)
                        .build();

                company = companyRepo.save(company);

                chartOfAccountsService.createDefaultCOAForCompany(company);

                // 3. Create EMPLOYEE record for this admin user
                Employee adminEmployee = Employee.builder()
                        .employeeNo(1L)   // first employee for first company
                        .firstName("Admin")
                        .lastName("User")
                        .phoneNo("+97412345678")
                        .company(company)    // attach company
                        .user(admin)         // link login user
                        .build();

                employeeRepo.save(adminEmployee);
            }
        };
    }
}

