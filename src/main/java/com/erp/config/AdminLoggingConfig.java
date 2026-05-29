package com.erp.config;

import com.erp.logging.AdminSystemLogAppender;
import com.erp.service.admin.AdminSystemLogService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AdminLoggingConfig {

    public AdminLoggingConfig(AdminSystemLogService adminSystemLogService) {
        AdminSystemLogAppender.setDelegate(adminSystemLogService);
    }
}
