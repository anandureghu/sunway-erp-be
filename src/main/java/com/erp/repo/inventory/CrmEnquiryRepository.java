package com.erp.repo.inventory;

import com.erp.domain.inventory.CrmEnquiry;
import com.erp.domain.inventory.CrmPipelineStatus;
import com.erp.domain.inventory.CrmProspectType;
import com.erp.domain.inventory.CrmRelationshipStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrmEnquiryRepository extends JpaRepository<CrmEnquiry, Long> {

    Optional<CrmEnquiry> findByIdAndCompanyId(Long id, Long companyId);

    @Query("""
            SELECT e FROM CrmEnquiry e
            WHERE e.company.id = :companyId
              AND (:pipeline IS NULL OR e.pipelineStatus = :pipeline)
              AND (:stage IS NULL OR e.relationshipStage = :stage)
              AND (:type IS NULL OR e.prospectType = :type)
              AND (
                   :q IS NULL OR :q = ''
                   OR LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.organizationName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.email, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.interest, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<CrmEnquiry> search(
            @Param("companyId") Long companyId,
            @Param("pipeline") CrmPipelineStatus pipeline,
            @Param("stage") CrmRelationshipStage stage,
            @Param("type") CrmProspectType type,
            @Param("q") String q,
            Pageable pageable
    );
}
