package com.erp.service.admin;

import com.erp.domain.User;
import com.erp.domain.admin.AdminSystemLog;
import com.erp.dto.admin.AdminSystemLogResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.UserRepository;
import com.erp.repo.admin.AdminSystemLogRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminSystemLogService {

    public static final String MDC_USER_ID = "adminLogUserId";
    public static final String MDC_USER_EMAIL = "adminLogUserEmail";
    public static final String MDC_USER_USERNAME = "adminLogUserUsername";
    public static final String MDC_COMPANY_ID = "adminLogCompanyId";
    public static final String MDC_REQUEST_URI = "adminLogRequestUri";
    public static final String MDC_REQUEST_METHOD = "adminLogRequestMethod";

    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_STACK = 16000;

    private final AdminSystemLogRepository repo;
    private final UserRepository userRepository;

    public AdminSystemLogService(AdminSystemLogRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLogEvent(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        String level = event.getLevel() != null ? event.getLevel().toString() : "WARN";
        if (!"WARN".equals(level) && !"ERROR".equals(level)) {
            return;
        }
        if (!shouldPersistToAdminTable(event)) {
            return;
        }

        Long userId = parseLong(mdcValue(event, MDC_USER_ID));
        String email = trimToNull(mdcValue(event, MDC_USER_EMAIL));
        String username = trimToNull(mdcValue(event, MDC_USER_USERNAME));
        Long companyId = parseLong(mdcValue(event, MDC_COMPANY_ID));

        if (userId != null) {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (email == null) {
                    email = user.getEmail();
                }
                if (username == null) {
                    username = user.getUsername();
                }
                // Do not fall back to users.company_id — it is stale for multi-company users.
            }
        }

        String requestUri = trimToNull(mdcValue(event, MDC_REQUEST_URI));
        String loggerName = event.getLoggerName();
        String module = AdminLogModuleResolver.resolve(requestUri, loggerName);

        String message = truncate(event.getFormattedMessage(), MAX_MESSAGE);
        String stack = buildStackTrace(event);

        AdminSystemLog row = AdminSystemLog.builder()
                .level(level)
                .module(module)
                .loggerName(truncate(loggerName, 255))
                .message(message != null && !message.isBlank() ? message : level)
                .stackTrace(stack)
                .userId(userId)
                .userEmail(truncate(email, 255))
                .userUsername(truncate(username, 255))
                .companyId(companyId)
                .requestMethod(truncate(trimToNull(mdcValue(event, MDC_REQUEST_METHOD)), 16))
                .requestUri(truncate(requestUri, 512))
                .build();

        repo.save(row);
    }

    /**
     * Skip framework startup/configuration noise; only record issues during real HTTP API traffic.
     */
    private static boolean shouldPersistToAdminTable(ILoggingEvent event) {
        String requestUri = trimToNull(mdcValue(event, MDC_REQUEST_URI));
        if (requestUri == null) {
            return false;
        }
        String logger = event.getLoggerName();
        if (logger != null) {
            String ln = logger;
            if (ln.startsWith("org.springframework")
                    || ln.startsWith("org.hibernate")
                    || ln.startsWith("org.apache")
                    || ln.startsWith("com.zaxxer")
                    || ln.startsWith("org.flyway")
                    || ln.startsWith("ch.qos.logback")) {
                return false;
            }
        }
        String message = event.getFormattedMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("spring.jpa.open-in-view")) {
                return false;
            }
            if (lower.contains("authenticationmanager configured with an authenticationprovider")) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public AdminSystemLogResponseDTO getById(Long id) {
        return repo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("System log not found"));
    }

    @Transactional(readOnly = true)
    public Page<AdminSystemLogResponseDTO> list(
            String level,
            String module,
            String search,
            Long userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        return repo.findAll(buildListSpecification(level, module, search, userId, from, to), pageable)
                .map(this::toDto);
    }

    private static Specification<AdminSystemLog> buildListSpecification(
            String level,
            String module,
            String search,
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (level != null && !level.isBlank()) {
                predicates.add(cb.equal(root.get("level"), level.trim().toUpperCase()));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module.trim()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern),
                        cb.like(cb.lower(root.get("userEmail")), pattern),
                        cb.like(cb.lower(root.get("userUsername")), pattern),
                        cb.like(cb.lower(root.get("requestUri")), pattern),
                        cb.like(cb.lower(root.get("loggerName")), pattern)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        from.atStartOfDay(ZoneOffset.UTC).toInstant()
                ));
            }
            if (to != null) {
                predicates.add(cb.lessThan(
                        root.get("createdAt"),
                        to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                ));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminSystemLogResponseDTO toDto(AdminSystemLog log) {
        return AdminSystemLogResponseDTO.builder()
                .id(log.getId())
                .createdAt(log.getCreatedAt())
                .level(log.getLevel())
                .module(log.getModule())
                .loggerName(log.getLoggerName())
                .message(log.getMessage())
                .stackTrace(log.getStackTrace())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .userUsername(log.getUserUsername())
                .companyId(log.getCompanyId())
                .requestMethod(log.getRequestMethod())
                .requestUri(log.getRequestUri())
                .build();
    }

    private static String buildStackTrace(ILoggingEvent event) {
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy == null) {
            return null;
        }
        String stack = ThrowableProxyUtil.asString(proxy);
        return truncate(stack, MAX_STACK);
    }

    private static Long parseLong(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String mdcValue(ILoggingEvent event, String key) {
        if (event == null || key == null) {
            return null;
        }
        if (event.getMDCPropertyMap() == null || event.getMDCPropertyMap().isEmpty()) {
            return null;
        }
        return event.getMDCPropertyMap().get(key);
    }
}
