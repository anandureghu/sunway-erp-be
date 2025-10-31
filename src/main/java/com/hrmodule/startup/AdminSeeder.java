package com.hrmodule.startup;

import com.hrmodule.domain.Role;
import com.hrmodule.domain.User;
import com.hrmodule.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {
    @Bean CommandLineRunner seedAdmin(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.count() == 0) {
                User admin = new User();
                admin.setFullName("Admin User");
                admin.setEmail("admin@hr.local");
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                admin.setRole(Role.ADMIN);
                repo.save(admin);
            }
        };
    }
}
