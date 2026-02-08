package com.erp.seed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
public class DatabaseInitRunner implements CommandLineRunner {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {

        entityManager.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS employee_no_seq (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        PRIMARY KEY (id)
                    )
                """).executeUpdate();
    }
}

