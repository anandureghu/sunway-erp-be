package com.erp.repo.subscription;

import com.erp.domain.subscription.CompanySubscription;
import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

    Optional<CompanySubscription> findByCompanyId(Long companyId);

    List<CompanySubscription> findByStatusIn(Collection<SubscriptionStatus> statuses);

    @Query("""
            SELECT cs FROM CompanySubscription cs
            LEFT JOIN com.erp.domain.hr.Company co ON co.id = cs.companyId
            WHERE (:status IS NULL OR cs.status = :status)
              AND (:planType IS NULL OR cs.planType = :planType)
              AND (:companyId IS NULL OR cs.companyId = :companyId)
              AND (
                    :companyCode IS NULL OR :companyCode = ''
                    OR LOWER(COALESCE(co.companyCode, '')) LIKE LOWER(CONCAT('%', :companyCode, '%'))
                  )
              AND (
                    :expiringBefore IS NULL
                    OR (cs.endsAt IS NOT NULL AND cs.endsAt <= :expiringBefore
                        AND cs.status IN (
                            com.erp.domain.subscription.SubscriptionStatus.ACTIVE,
                            com.erp.domain.subscription.SubscriptionStatus.EXPIRING
                        ))
                  )
              AND (
                    :q IS NULL OR :q = ''
                    OR LOWER(COALESCE(co.companyName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(co.companyCode, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(cs.notes, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CAST(cs.planType AS string)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(CAST(cs.status AS string)) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR CAST(cs.companyId AS string) LIKE CONCAT('%', :q, '%')
                  )
            """)
    Page<CompanySubscription> search(
            @Param("status") SubscriptionStatus status,
            @Param("planType") SubscriptionPlanType planType,
            @Param("companyId") Long companyId,
            @Param("companyCode") String companyCode,
            @Param("expiringBefore") LocalDate expiringBefore,
            @Param("q") String q,
            Pageable pageable
    );

    long countByStatus(SubscriptionStatus status);

    long countByPlanType(SubscriptionPlanType planType);

    @Query("""
            SELECT COUNT(cs) FROM CompanySubscription cs
            WHERE cs.endsAt IS NOT NULL
              AND cs.endsAt <= :before
              AND cs.status IN (
                  com.erp.domain.subscription.SubscriptionStatus.ACTIVE,
                  com.erp.domain.subscription.SubscriptionStatus.EXPIRING
              )
            """)
    long countExpiringBefore(@Param("before") LocalDate before);

    @Query("""
            SELECT cs FROM CompanySubscription cs
            WHERE cs.endsAt IS NOT NULL
              AND cs.planType <> com.erp.domain.subscription.SubscriptionPlanType.FREE
              AND cs.status NOT IN (
                  com.erp.domain.subscription.SubscriptionStatus.CANCELLED
              )
            """)
    List<CompanySubscription> findAllWithEndDateForReconcile();
}
