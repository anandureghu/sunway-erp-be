package com.erp.repo.appraisal;

import com.erp.domain.appraisal.AppraisalRoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppraisalRoleConfigRepository
        extends JpaRepository<AppraisalRoleConfig, Long> {

    Optional<AppraisalRoleConfig>
    findByConfigIdAndJobCode(Long configId, String jobCode);
}