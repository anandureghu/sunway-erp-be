package com.erp.repo.subscription;

import com.erp.domain.subscription.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByCompanySubscriptionIdOrderByPaidOnDescCreatedAtDesc(Long companySubscriptionId);

    List<SubscriptionPayment> findByCompanyIdOrderByPaidOnDescCreatedAtDesc(Long companyId);

    Optional<SubscriptionPayment> findByIdempotencyKey(String idempotencyKey);

    Optional<SubscriptionPayment> findFirstByCompanyIdOrderByPaidOnDescCreatedAtDesc(Long companyId);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p
            WHERE p.paidOn >= :from AND p.paidOn <= :to
            """)
    BigDecimal sumAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT FUNCTION('DATE_FORMAT', p.paidOn, '%Y-%m') AS monthKey, COALESCE(SUM(p.amount), 0)
            FROM SubscriptionPayment p
            WHERE p.paidOn >= :from AND p.paidOn <= :to
            GROUP BY FUNCTION('DATE_FORMAT', p.paidOn, '%Y-%m')
            ORDER BY monthKey
            """)
    List<Object[]> sumByMonthBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
