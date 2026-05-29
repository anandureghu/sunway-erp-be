package com.erp.domain.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "admin_system_logs")
public class AdminSystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(name = "logger_name", length = 255)
    private String loggerName;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "user_username", length = 255)
    private String userUsername;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "request_method", length = 16)
    private String requestMethod;

    @Column(name = "request_uri", length = 512)
    private String requestUri;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
