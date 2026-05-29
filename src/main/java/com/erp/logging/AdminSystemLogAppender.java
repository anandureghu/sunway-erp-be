package com.erp.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.erp.service.admin.AdminSystemLogService;

/**
 * Persists WARN/ERROR log events to {@code admin_system_logs}. Wired from logback-spring.xml;
 * delegate is set at runtime by {@link com.erp.config.AdminLoggingConfig}.
 */
public class AdminSystemLogAppender extends AppenderBase<ILoggingEvent> {

    private static volatile AdminSystemLogService delegate;

    public static void setDelegate(AdminSystemLogService service) {
        delegate = service;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        String logger = event.getLoggerName();
        if (logger != null && logger.startsWith("com.erp.service.admin.AdminSystemLogService")) {
            return;
        }
        if (logger != null && logger.startsWith("com.erp.logging.AdminSystemLogAppender")) {
            return;
        }
        AdminSystemLogService service = delegate;
        if (service == null) {
            return;
        }
        String level = event.getLevel() != null ? event.getLevel().toString() : "";
        if (!"WARN".equals(level) && !"ERROR".equals(level)) {
            return;
        }
        try {
            service.persistLogEvent(event);
        } catch (Exception ignored) {
            // Never fail the request thread because audit logging failed.
        }
    }
}
