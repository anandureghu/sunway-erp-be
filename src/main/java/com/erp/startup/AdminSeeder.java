package com.erp.startup;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeContactInfo;
import com.erp.domain.Role;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.repo.hr.CompanyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@Configuration
public class AdminSeeder {

    @Bean
    CommandLineRunner seedAdmin(
            UserRepository userRepo,
            CompanyRepository companyRepo,
            EmployeeRepository employeeRepo,
            PasswordEncoder encoder,
            EmployeeContactInfoRepository contactRepo
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
                admin.setCreatedAt(Instant.now());

                // 2. Create default company
                Company company = Company.builder()
                        .companyName("Default Admin Company")
                        .noOfEmployees("10")
                        .crNo(10001L)
                        .companyCode("100")
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
                        .createdAt(Instant.now())
                        .build();

                company = companyRepo.save(company);
                admin.setCompany(company);
                admin = userRepo.save(admin);

                // 3. Create EMPLOYEE record for this admin user
                Employee adminEmployee = Employee.builder()
                        .employeeNo("ADMIN")   // first employee for first company
                        .firstName("Super")
                        .lastName("Admin")
                        .company(company)    // attach company
                        .user(admin)         // link login user
                        .createdAt(Instant.now())
                        .build();

                employeeRepo.save(adminEmployee);

                EmployeeContactInfo contactInfo = EmployeeContactInfo.builder()
                        .phone("+9876543210")
                        .email("admin@hr.local")
                        .employee(adminEmployee)
                        .build();

                contactRepo.save(contactInfo);

            }
        };
    }
}

