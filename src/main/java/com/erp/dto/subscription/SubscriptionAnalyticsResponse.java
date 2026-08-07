package com.erp.dto.subscription;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionAnalyticsResponse {

    private long totalCompanies;
    private Map<String, Long> countByStatus;
    private Map<String, Long> countByPlanType;
    private long expiringIn7Days;
    private long expiringIn30Days;
    private BigDecimal revenueCollectedInRange;
    private BigDecimal estimatedMonthlyRecurring;
    private long newInPeriod;
    private long expiredInPeriod;
    private List<MonthlyRevenuePoint> paymentsByMonth;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyRevenuePoint {
        private String month;
        private BigDecimal amount;
    }
}
