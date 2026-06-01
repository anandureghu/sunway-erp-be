package com.erp.repo.admin;

import com.erp.domain.admin.AdminSystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminSystemLogRepository
        extends JpaRepository<AdminSystemLog, Long>, JpaSpecificationExecutor<AdminSystemLog> {

    Page<AdminSystemLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
