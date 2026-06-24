package com.erp.seed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Seeds baseline HR module permissions for company roles on every startup.
 * Covers existing companies and any new companies/roles created at runtime.
 */
@Component
@Order(50)
public class CompanyRolePermissionSeeder implements ApplicationRunner {

    private static final String SEED_SCRIPT = "db/seed/company_role_permissions.sql";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        String sql = StreamUtils.copyToString(
                new ClassPathResource(SEED_SCRIPT).getInputStream(),
                StandardCharsets.UTF_8
        );

        // Strip comments BEFORE splitting on ';'. A comment line may itself
        // contain a ';' — splitting first would slice the comment in two and
        // leave the tail as a bogus statement (the rest no longer starts with
        // '--', so it survives the per-statement strip).
        Arrays.stream(stripComments(sql).split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .forEach(statement -> entityManager.createNativeQuery(statement).executeUpdate());
    }

    private static String stripComments(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        return cleaned.toString();
    }
}
